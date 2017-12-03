package com.github.mproberts.rxdatabinding.navigation;

import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

@AutoService(Processor.class)
public class NavigationProcessor extends AbstractProcessor {
    private static final String CODEGEN_PREFIX = "AndroidBinding";
    private static final TypeName CONTEXT_TYPE_NAME = ClassName.get("android.content", "Context");

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Sets.newHashSet(
                NavigationSource.class.getCanonicalName(),
                ActivityNavigation.class.getCanonicalName());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        Set<? extends Element> rootElements = roundEnvironment.getRootElements();

        for (Element element : roundEnvironment.getElementsAnnotatedWith(NavigationSource.class)) {
            NavigationSource sourceAnnotation = element.getAnnotation(NavigationSource.class);

            String baseActivityName = CodegenTools.getQualifiedClassName(sourceAnnotation::baseActivity);

            String packageName = CodegenTools.packageOf((TypeElement) element);
            String className = CodegenTools.classNameOf((TypeElement) element);

            TypeSpec.Builder navigationSourceRoot = TypeSpec.classBuilder(CODEGEN_PREFIX + className)
                    .addModifiers(Modifier.PUBLIC)
                    .superclass(TypeName.get(element.asType()));

            navigationSourceRoot.addField(CONTEXT_TYPE_NAME, "_context", Modifier.PRIVATE, Modifier.FINAL);
            navigationSourceRoot.addMethod(MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(CONTEXT_TYPE_NAME, "context")
                    .addCode("_context = context;")
                    .build());

            Map<String, ActivityMethodProcessor> activityMethodProcessorMap = new HashMap<>();
            NavigationSourceInfoProvider info = new NavigationSourceInfoProvider((TypeElement) element, rootElements, className, packageName, baseActivityName);

            List<? extends Element> sourceMethods = element.getEnclosedElements();
            for (Element activityMethod : sourceMethods) {
                ActivityNavigation activityAnnotation = activityMethod.getAnnotation(ActivityNavigation.class);

                if (activityAnnotation == null) {
                    continue;
                }

                String targetClassFullName = CodegenTools.getQualifiedClassName(activityAnnotation::value);
                ExecutableElement method = (ExecutableElement) activityMethod;

                ActivityMethodProcessor methodProcessor = activityMethodProcessorMap.get(targetClassFullName);

                if (methodProcessor == null) {
                    methodProcessor = new ActivityMethodProcessor(CODEGEN_PREFIX, info, navigationSourceRoot);
                    activityMethodProcessorMap.put(targetClassFullName, methodProcessor);
                }

                methodProcessor.addMethod(method, activityAnnotation);
            }

            for (ActivityMethodProcessor methodProcessor : activityMethodProcessorMap.values()) {
                methodProcessor.finish(roundEnvironment, processingEnv);
            }

            JavaFile javaFile = JavaFile.builder(packageName, navigationSourceRoot.build()).build();

            try {
                javaFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                roundEnvironment.errorRaised();
            }
        }

        return false;
    }
}