package org.patcher;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

public class StringClassVisitor extends ClassVisitor {

  private final String toReplace;
  private final String replaceWith;

  public StringClassVisitor(int version, ClassVisitor visitor, String toReplace, String replaceWith) {
    super(version, visitor);
    this.toReplace = toReplace;
    this.replaceWith = replaceWith;
  }

  @Override
  public FieldVisitor visitField(int access, String name, String descriptor, String signature,
      Object value) {
    if (value instanceof String && value.equals(toReplace)) {
      return super.visitField(access, name, descriptor, signature, replaceWith);
    }
    return super.visitField(access, name, descriptor, signature, value);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
      String[] exceptions) {
    return new StringMethodVisitor(super.visitMethod(access, name, descriptor, signature,
        exceptions), toReplace, replaceWith);
  }
}
