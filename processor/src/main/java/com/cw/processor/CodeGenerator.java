package com.cw.processor;

import com.cw.core.annotations.Gpollo;
import com.cw.core.annotations.contrace.GpolloBinder;
import com.cw.core.annotations.contrace.GpolloBinderGenerator;
import com.cw.core.annotations.entity.BackpressureMode;
import com.cw.core.annotations.entity.Null;
import com.cw.core.annotations.entity.GpolloBinderImpl;
import com.cw.core.annotations.entity.ThreadMode;
import com.cw.core.annotations.utils.GpollpUtil;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import io.reactivex.subscribers.DisposableSubscriber;


/**
 * @author cw
 * @date 2017/12/18
 */
class CodeGenerator {

    private static final String GENERATE_PACKAGE_NAME = "com.cw.gpollo.generate";
    private static final String GENERATE_CLASS_NAME = "GpolloBinderGeneratorImpl";
    private static final String GPOLLO_BINDER_NAME = "gpolloBinder";
    private static final String ACTION1_CALL_PARAM = "callParam";
    private static final String GENERATE_PARAM = "bindObject";

    private List<GpolloDescriptor> mGpolloDescriptors;
    private Filer mFiler;
    private String mTag;

    private CodeGenerator(ArrayList<GpolloDescriptor> gpolloDescriptors, Filer filer, String tag) {
        this.mGpolloDescriptors = gpolloDescriptors;
        this.mFiler = filer;
        this.mTag = tag;
    }

    static CodeGenerator create(ArrayList<GpolloDescriptor> gpolloDescriptors, Filer filer, String tag) {
        return new CodeGenerator(gpolloDescriptors, filer, tag);
    }

    void createJavaFile() {
        try {
            getBinderGeneratorJavaFile().writeTo(mFiler);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JavaFile getBinderGeneratorJavaFile() {
        return JavaFile.builder(GENERATE_PACKAGE_NAME, getGeneratorTypeSpec())
                .addStaticImport(ThreadMode.MAIN)
                .addStaticImport(ThreadMode.IO)
                .addStaticImport(ThreadMode.SINGLE)
                .addStaticImport(ThreadMode.NEW)
                .build();
    }

    //----------------------------------------------------------------------------------------------

    /**
     * public final class GpolloBinderGeneratorImpl_app implements GpolloBinderGenerator {...}
     */
    private TypeSpec getGeneratorTypeSpec() {
        return TypeSpec.classBuilder(GENERATE_CLASS_NAME + "_" + mTag)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(GpolloBinderGenerator.class)
                .addField(getSingleInstanceFileSpec())
                .addMethod(getSingleInstanceMethodSpec())
                .addMethod(getGenerateFunctionMethodSpec())
                .build();
    }

    /**
     * private static GpolloBinderGenerator sInstance;
     */
    private FieldSpec getSingleInstanceFileSpec() {
        return FieldSpec.builder(GpolloBinderGenerator.class, "instance", Modifier.PRIVATE, Modifier.STATIC)
                .build();
    }

    /**
     * public static synchronized GpolloBinderGenerator instance() {
     * if (null == sInstance) {
     * sInstance = new GpolloBinderGenerator();
     * }
     * return sInstance;
     * }
     */
    private MethodSpec getSingleInstanceMethodSpec() {
        return MethodSpec.methodBuilder("instance")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.SYNCHRONIZED)
                .returns(GpolloBinderGenerator.class)
                .beginControlFlow("if (instance == null)")
                .addStatement("instance = new " + GENERATE_CLASS_NAME + "_" + mTag + "()")
                .endControlFlow()
                .addStatement("return instance")
                .build();
    }

    /**
     * public GpolloBinder generate(final Object bindObject) {...}
     */
    private MethodSpec getGenerateFunctionMethodSpec() {
        MethodSpec.Builder builder = MethodSpec.methodBuilder("generate")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(GpolloBinder.class)
                .addParameter(Object.class, GENERATE_PARAM, Modifier.FINAL)
                .addStatement("$T " + GPOLLO_BINDER_NAME + " = new $T()", GpolloBinderImpl.class, GpolloBinderImpl.class);

        if (mGpolloDescriptors != null) {
            for (GpolloDescriptor gpolloDescriptor : mGpolloDescriptors) {
                getSingleBinderStatement(builder, gpolloDescriptor);
            }
        }
        return builder.addStatement("return " + GPOLLO_BINDER_NAME).build();
    }

    /**
     * GpolloBinderImpl gpolloBinder = new GpolloBinderImpl();
     * if (MainActivity.class.isAssignableFrom(bindObject.getClass())) {
     * gpolloBinder.add(Gpollo.getDefault().toObservable(new String[]{...}, ....class).subscribe(new Action1<...>() {
     * ......
     * }
     */
    private void getSingleBinderStatement(MethodSpec.Builder builder, GpolloDescriptor gpolloDescriptors) {
        List<? extends VariableElement> parameters = gpolloDescriptors.methodElement.getParameters();
        TypeMirror typeMirror = null;
        if (parameters.size() > 1) {
            throw new RuntimeException("Gpollp error : receive event method can only have one parameter");
        }
        if (parameters.size() == 1) {
            typeMirror = parameters.get(0).asType();
        }
        boolean canReceiveNull = gpolloDescriptors.canReceiveNull;
        ThreadMode subscribeOn = gpolloDescriptors.subscribeOn;
        ThreadMode observeOn = gpolloDescriptors.observeOn;
        BackpressureMode backpressure = gpolloDescriptors.backpressure;
        String methodName = gpolloDescriptors.methodElement.getSimpleName().toString();
        String clazzType = gpolloDescriptors.methodElement.getEnclosingElement().asType().toString().replaceAll("<.*>", "");
        builder.beginControlFlow("if (" + clazzType + ".class.isAssignableFrom(" + GENERATE_PARAM + ".getClass()))")
                .addStatement(GPOLLO_BINDER_NAME + ".add($T.toObservable(new String[]{"
                        + GpollpUtil.split(gpolloDescriptors.tags, ",") + "}, $T.class)"
                        + getBackpressureMethodCode(backpressure)
                        + getSubscribeOnMethodCode(subscribeOn)
                        + getObserveOnMethodCode(observeOn)
                        + getSubscribeWithCode(typeMirror, clazzType, methodName, canReceiveNull) + ")", Gpollo.class, Object.class)
                .endControlFlow();
    }

    /**
     * .onBackpressurebuffer()
     */
    private String getBackpressureMethodCode(BackpressureMode backpressureMode) {
        switch (backpressureMode) {
            case BUFFER:
                return ".onBackpressureBuffer()";
            case DROP:
                return ".onBackpressureDrop()";
            case LATEST:
                return ".onBackpressureLatest()";
        }
        return "";
    }

    /**
     * .subscribeOn(Gpollo.getSchedulerProvider().get(ThreadMode.IO))
     */
    private String getSubscribeOnMethodCode(ThreadMode subscribeOn) {
        return ".subscribeOn(Gpollo.getSchedulerProvider().get(" + subscribeOn.name() + "))";
    }

    /**
     * .observeOn(Gpollo.getSchedulerProvider().get(ThreadMode.MAIN))
     */
    private String getObserveOnMethodCode(ThreadMode observeOn) {
        return ".observeOn(Gpollo.getSchedulerProvider().get(" + observeOn.name() + "))";
    }

    /**
     * .subscribeWith(new DisposableSubscriber<Object>(){
     * });
     */
    private CodeBlock getSubscribeWithCode(TypeMirror typeMirror, String clazzType, String methodName, boolean canReceiveNull) {
        return CodeBlock.builder().add(".subscribeWith(new $T<Object>(){"
                + getOnCompleteMethodCode() + getOnErrorMethodCode()
                + getOnNextMethodCode(typeMirror, clazzType, methodName, canReceiveNull) + "})", DisposableSubscriber.class)
                .build();
    }

    /**
     * public void onComplete(Object o) {
     * }
     */
    private MethodSpec getOnCompleteMethodCode() {
        return MethodSpec.methodBuilder("onComplete")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .build();
    }

    /**
     * public void onError(java.lang.Throwable t) {
     * }
     */
    private MethodSpec getOnErrorMethodCode() {
        return MethodSpec.methodBuilder("onError")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(Throwable.class, "t")
                .addCode("t.printStackTrace();")
                .build();
    }

    /**
     * public void onNext(Object callParam) {
     * }
     */
    private MethodSpec getOnNextMethodCode(TypeMirror typeMirror, String clazzType, String methodName, boolean canReceiveNull) {
        return MethodSpec.methodBuilder("onNext")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(Object.class, ACTION1_CALL_PARAM)
                .addCode(getCallMethodCode(typeMirror, clazzType, methodName, canReceiveNull))
                .build();
    }

    /**
     * MainActivity subscribe = (com.cw.gpollo.MainActivity) bindObject;
     * subscribe.add(callParam);
     */
    private CodeBlock getCallMethodCode(TypeMirror typeMirror, String clazzType, String methodName, boolean canReceiveNull) {
        CodeBlock.Builder builder = CodeBlock.builder().addStatement(clazzType + " subscribe = (" + clazzType + ") " + GENERATE_PARAM);
        if (typeMirror != null) {
            if (canReceiveNull) {
                builder = builder.beginControlFlow("if(" + ACTION1_CALL_PARAM + " instanceof $T)", Null.class)
                        .addStatement("subscribe." + methodName + "(null)")
                        .endControlFlow();
            }
            builder = builder.beginControlFlow("if(" + ACTION1_CALL_PARAM + " instanceof " + GpollpUtil.parseVariableType(typeMirror) + ")")
                    .addStatement("subscribe." + methodName + "(($T)" + ACTION1_CALL_PARAM + ")", typeMirror)
                    .endControlFlow();
        } else {
            builder.addStatement("subscribe." + methodName + "()");
        }
        return builder.build();
    }
}