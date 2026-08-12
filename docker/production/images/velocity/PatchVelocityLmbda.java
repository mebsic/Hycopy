import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class PatchVelocityLmbda {
    private static final String TARGET_CLASS = "org/lanternpowered/lmbda/InternalMethodHandles.class";
    private static final String TARGET_METHOD = "findDefineHiddenClassMethodHandle";
    private static final String TARGET_DESCRIPTOR = "()Ljava/lang/invoke/MethodHandle;";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: PatchVelocityLmbda <velocity.jar>");
        }

        Path jar = Path.of(args[0]);
        Path patched = Files.createTempFile(jar.getParent(), "velocity-lmbda-patched-", ".jar");
        boolean classFound;
        boolean methodPatched;

        try (JarFile input = new JarFile(jar.toFile())) {
            Manifest manifest = input.getManifest();
            try (OutputStream fileOut = Files.newOutputStream(patched);
                 JarOutputStream output = manifest == null
                         ? new JarOutputStream(fileOut)
                         : new JarOutputStream(fileOut, manifest)) {
                PatchState state = copyAndPatch(input, output);
                classFound = state.classFound;
                methodPatched = state.methodPatched;
            }
        } catch (Exception ex) {
            Files.deleteIfExists(patched);
            throw ex;
        }

        if (!classFound) {
            Files.deleteIfExists(patched);
            System.out.println("[bootstrap] Velocity lmbda patch skipped: target class not present.");
            return;
        }
        if (!methodPatched) {
            Files.deleteIfExists(patched);
            System.out.println("[bootstrap] Velocity lmbda patch skipped: hidden-class method not present.");
            return;
        }

        Files.move(patched, jar, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("[bootstrap] Velocity lmbda patch applied.");
    }

    private static PatchState copyAndPatch(JarFile input, JarOutputStream output) throws IOException {
        PatchState state = new PatchState();
        Enumeration<JarEntry> entries = input.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if ("META-INF/MANIFEST.MF".equalsIgnoreCase(name) || isSignatureFile(name)) {
                continue;
            }

            byte[] data;
            try (InputStream entryInput = input.getInputStream(entry)) {
                data = readAllBytes(entryInput);
            }

            if (TARGET_CLASS.equals(name)) {
                state.classFound = true;
                PatchedClass patchedClass = patchClass(data);
                data = patchedClass.bytes;
                state.methodPatched = patchedClass.methodPatched;
            }

            JarEntry replacement = new JarEntry(name);
            replacement.setTime(entry.getTime());
            output.putNextEntry(replacement);
            output.write(data);
            output.closeEntry();
        }
        return state;
    }

    private static PatchedClass patchClass(byte[] original) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, 0);
        boolean[] methodPatched = new boolean[] {false};
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    String[] exceptions
            ) {
                if (TARGET_METHOD.equals(name) && TARGET_DESCRIPTOR.equals(descriptor)) {
                    methodPatched[0] = true;
                    MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                    method.visitCode();
                    method.visitInsn(Opcodes.ACONST_NULL);
                    method.visitInsn(Opcodes.ARETURN);
                    method.visitMaxs(1, 0);
                    method.visitEnd();
                    return null;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        };
        reader.accept(visitor, 0);
        return new PatchedClass(writer.toByteArray(), methodPatched[0]);
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean isSignatureFile(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/")
                && (upper.endsWith(".SF")
                || upper.endsWith(".RSA")
                || upper.endsWith(".DSA")
                || upper.endsWith(".EC"));
    }

    private static final class PatchState {
        private boolean classFound;
        private boolean methodPatched;
    }

    private static final class PatchedClass {
        private final byte[] bytes;
        private final boolean methodPatched;

        private PatchedClass(byte[] bytes, boolean methodPatched) {
            this.bytes = bytes;
            this.methodPatched = methodPatched;
        }
    }
}
