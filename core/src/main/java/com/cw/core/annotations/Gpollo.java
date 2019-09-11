package com.cw.core.annotations;

import com.cw.core.annotations.contrace.GpolloBinder;
import com.cw.core.annotations.contrace.GpolloBinderGenerator;
import com.cw.core.annotations.entity.Null;
import com.cw.core.annotations.entity.Event;
import com.cw.core.annotations.entity.GpolloBinderImpl;
import com.cw.core.annotations.entity.SchedulerProvider;

import org.reactivestreams.Publisher;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;


/**
 * @author cw
 * @date 2017/12/20
 */
public class Gpollo {

    private static volatile Gpollo mInstance;
    private static String[] sModules = new String[]{};
    private static String sMainModule;

    private final FlowableProcessor<Event> mBus;
    private Map<String, GpolloBinderGenerator> mGeneratorMap = new HashMap<>();
    private SchedulerProvider mSchedulerProvider;

    private Gpollo() {
        PublishProcessor<Event> objectPublishProcessor = PublishProcessor.create();
        mBus = objectPublishProcessor.toSerialized();
        for (String model : sModules) {
            generator(model);
        }
        generator(sMainModule);
    }

    @SuppressWarnings("unchecked")
    private void generator(String modelName) {
        try {
            Class<GpolloBinderGenerator> generatorClass = (Class<GpolloBinderGenerator>) Class.forName("com.cw.gpollo.generate.GpolloBinderGeneratorImpl_" + modelName);
            Method method = generatorClass.getMethod("instance");
            GpolloBinderGenerator mGenerator = (GpolloBinderGenerator) method.invoke(null);
            mGeneratorMap.put(modelName, mGenerator);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Gpollo getDefault() {
        if (mInstance == null) {
            synchronized (Gpollo.class) {
                if (mInstance == null) {
                    mInstance = new Gpollo();
                }
            }
        }
        return mInstance;
    }

    public static void init(Scheduler scheduler, String mainModule, String... modules) {
        if (mainModule == null || mainModule.length() == 0) {
            throw new RuntimeException("mainModule must has");
        }
        sMainModule = mainModule;
        sModules = modules;
        Gpollo.getDefault().mSchedulerProvider = SchedulerProvider.create(scheduler);
    }

    public static SchedulerProvider getSchedulerProvider() {
        if (Gpollo.getDefault().mSchedulerProvider == null) {
            throw new RuntimeException("Gpollo must be init");
        }
        return Gpollo.getDefault().mSchedulerProvider;
    }

    public static GpolloBinder bind(Object o) {
        if (null == o) {
            throw new RuntimeException("object to subscribe must not be null");
        }
        GpolloBinderGenerator generator = null;
        String packageName = o.getClass().getPackage().getName();
        for (String module : sModules) {
            if (packageName.contains("." + module)) {
                generator = Gpollo.getDefault().mGeneratorMap.get(module);
                break;
            }
        }
        if (generator == null) {
            generator = Gpollo.getDefault().mGeneratorMap.get(sMainModule);
        }
        if (generator == null) {
            return new GpolloBinderImpl();
        }
        return generator.generate(o);
    }

    public static void unBind(GpolloBinder bind) {
        if (bind != null) {
            bind.unbind();
        }
    }

    public static void post(String tag) {
        Gpollo.getDefault().mBus.onNext(new Event(tag, null));
    }

    public static void post(String tag, Object actual) {
        Gpollo.getDefault().mBus.onNext(new Event(tag, actual));
    }

    public static <T> Flowable<T> toObservable(final String[] tags, final Class<T> eventType) {
        return Gpollo.getDefault().mBus.filter(new Predicate<Event>() {
            @Override
            public boolean test(Event event) {
                return Arrays.asList(tags).contains(event.getTag())
                        && ((eventType.isInstance(event.getData()) || event.getData() == null));
            }
        }).flatMap(new Function<Event, Publisher<T>>() {
            @Override
            public Publisher<T> apply(Event event) {
                return Flowable.just(eventType.cast(event.getData() != null ? event.getData() : new Null()));
            }
        });
    }
}