package org.patcher;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public class StringTransformer implements ClassFileTransformer {

  private final String toReplace;
  private final String replaceWith;

  public StringTransformer(String toReplace, String replaceWith) {
    this.toReplace = toReplace;
    this.replaceWith = replaceWith;
  }

  @Override
  public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain, byte[] classfileBuffer) {
    ClassReader reader = new ClassReader(classfileBuffer);
    ClassWriter writer = new ClassWriter(reader, 0);
    StringClassVisitor visitor = new StringClassVisitor(Opcodes.ASM9, writer, toReplace, replaceWith);
    reader.accept(visitor, 0);
    return writer.toByteArray();
  }
}
