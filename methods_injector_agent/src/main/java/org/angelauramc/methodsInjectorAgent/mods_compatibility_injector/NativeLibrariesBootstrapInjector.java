package org.angelauramc.methodsInjectorAgent.mods_compatibility_injector;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Prevents crash when Minecraft 26.3+ bootstraps SDL on Android via NativeLibrariesBootstrap.loadSdl().
 */
public class NativeLibrariesBootstrapInjector extends ClassVisitor implements ClassFileTransformer {
    protected NativeLibrariesBootstrapInjector(int api) {
        super(api);
    }

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader l, String name, Class c,
                                    ProtectionDomain d, byte[] b) {
                if (!"com/mojang/blaze3d/platform/NativeLibrariesBootstrap".equals(name)) {
                    return null;
                }
                ClassReader cr = new ClassReader(b);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                ClassVisitor cv = new SafeSdlAdapter(cw);
                cr.accept(cv, 0);
                return cw.toByteArray();
            }
        });
    }

    public static class SafeSdlAdapter extends ClassVisitor {
        public SafeSdlAdapter(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            if (name.equals("loadSdl") && desc.equals("()V")) {
                try {
                    System.out.write("Amethyst-Android: Neutralizing NativeLibrariesBootstrap.loadSdl() for mobile compatibility...\n".getBytes());
                    System.out.flush();
                } catch (Exception ignored) {}
                return new MethodVisitor(this.api, null) {
                    @Override
                    public void visitCode() {
                        mv.visitCode();
                        // Safely return immediately to prevent NoClassDefFoundError or UnsatisfiedLinkError on Android
                        mv.visitInsn(Opcodes.RETURN);
                        mv.visitMaxs(0, 0);
                        mv.visitEnd();
                    }
                };
            }
            return mv;
        }
    }
}
