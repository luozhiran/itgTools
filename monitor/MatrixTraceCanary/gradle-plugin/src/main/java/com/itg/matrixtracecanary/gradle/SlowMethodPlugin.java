package com.itg.matrixtracecanary.gradle;

import com.android.build.api.instrumentation.FramesComputationMode;
import com.android.build.api.instrumentation.InstrumentationScope;
import com.android.build.api.instrumentation.InstrumentationParameters;
import com.android.build.api.variant.AndroidComponentsExtension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import kotlin.Unit;

/** Instruments classes in Android application or library projects with slow-method probes. */
public final class SlowMethodPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().withId("com.android.application", ignored -> configure(project));
        project.getPlugins().withId("com.android.library", ignored -> configure(project));
    }

    private void configure(Project project) {
        AndroidComponentsExtension<?, ?, ?> androidComponents =
                project.getExtensions().getByType(AndroidComponentsExtension.class);
        androidComponents.onVariants(androidComponents.selector().all(), variant -> {
            variant.getInstrumentation().transformClassesWith(
                    SlowMethodVisitorFactory.class,
                    InstrumentationScope.PROJECT,
                    (InstrumentationParameters.None params) -> Unit.INSTANCE);
            variant.getInstrumentation().setAsmFramesComputationMode(
                    FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS);
        });
    }
}
