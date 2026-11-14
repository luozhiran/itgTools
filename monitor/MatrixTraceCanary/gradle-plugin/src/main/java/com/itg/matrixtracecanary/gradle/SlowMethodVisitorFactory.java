package com.itg.matrixtracecanary.gradle;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

/** AGP creates an instance for each class; no shared mutable build state is required. */
public abstract class SlowMethodVisitorFactory
        implements AsmClassVisitorFactory<InstrumentationParameters.None> {
    private static final String RECORDER = "com/itg/matrixtracecanary/SlowMethodRecorder";
    private static final Type RECORDER_TYPE = Type.getObjectType(RECORDER);
    private static final Method ENTER = new Method("enter", "()J");
    private static final Method EXIT = new Method("exit", "(JLjava/lang/String;)V");

    @Override
    public boolean isInstrumentable(ClassData classData) {
        String name = classData.getClassName().replace('.', '/');
        return !name.startsWith("android/")
                && !name.startsWith("androidx/")
                && !name.startsWith("kotlin/")
                && !name.startsWith("java/")
                && !name.startsWith("javax/")
                && !name.startsWith("com/itg/matrixtracecanary/")
                && !name.startsWith("com/tencent/matrix/")
                && !name.contains("/databinding/")
                && !name.endsWith("/R")
                && !name.contains("/R$")
                && !name.endsWith("/BuildConfig");
    }

    @Override
    public ClassVisitor createClassVisitor(ClassContext context, ClassVisitor nextClassVisitor) {
        return new ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
            private String owner;

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                owner = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (next == null || name.startsWith("<")
                        || (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE
                        | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) {
                    return next;
                }
                String methodName = owner.replace('/', '.') + "#" + name + descriptor;
                return new AdviceAdapter(Opcodes.ASM9, next, access, name, descriptor) {
                    private final Label start = new Label();
                    private final Label end = new Label();
                    private final Label handler = new Label();
                    private int startedAt;

                    @Override protected void onMethodEnter() {
                        invokeStatic(RECORDER_TYPE, ENTER);
                        startedAt = newLocal(Type.LONG_TYPE);
                        storeLocal(startedAt);
                        mark(start);
                    }

                    @Override protected void onMethodExit(int opcode) {
                        if (opcode != ATHROW) {
                            recordExit();
                        }
                    }

                    private void recordExit() {
                        loadLocal(startedAt);
                        push(methodName);
                        invokeStatic(RECORDER_TYPE, EXIT);
                    }

                    @Override public void visitMaxs(int maxStack, int maxLocals) {
                        mark(end);
                        visitTryCatchBlock(start, end, handler, null);
                        mark(handler);
                        int thrown = newLocal(Type.getType(Throwable.class));
                        storeLocal(thrown);
                        recordExit();
                        loadLocal(thrown);
                        throwException();
                        super.visitMaxs(maxStack, maxLocals);
                    }
                };
            }
        };
    }
}
