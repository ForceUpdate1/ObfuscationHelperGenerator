package de.foorcee.mappings;

import com.sun.org.apache.bcel.internal.generic.ALOAD;
import com.sun.org.apache.bcel.internal.generic.ILOAD;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.tree.EntryTreeNode;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import de.foorcee.mappings.data.mojang.MojangMappings;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ClassTransformer {

    private Map<String, EntryTreeNode<EntryMapping>> mappings = new HashMap<>();

    public ClassTransformer(Ressource ressource, List<EntryTreeNode<EntryMapping>> list) throws IOException {

//        if(!ressource.getFileName().endsWith("/Entity.class")){
//            return;
//        }

        if (list == null) return;

        Function<String, String> remapFunction = s -> {
            String remap = MojangMappings.mojangToBukkitClassNames.get(s);
            System.out.println("remap: " + s + " ->" + remap);
            if(remap == null) {
                return s;
            }
            return "net/minecraft/server/v1_16_R2/" + remap;
        };

        for (EntryTreeNode<EntryMapping> node : list) {
            MethodEntry entry = (MethodEntry) node.getEntry();
            entry.getDesc().getReturnDesc().remap(remapFunction);
            entry.getDesc().getArgumentDescs().forEach(typeDescriptor -> typeDescriptor.remap(remapFunction));
            String remappedDesc = entry.getDesc().remap(remapFunction).toString();
           // System.out.println(ressource.getSimpleName() + " " +entry.getName() + " " + remappedDesc);
            String id = entry.getName() + remappedDesc;
            System.out.println("Load id: " + id);
            mappings.put(id, node);
        }

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassNode classNode = new ClassNode();
        ClassReader reader = new ClassReader(ressource.getData());
        reader.accept(classNode, 0);

        boolean modifyed = false;
        for (Object object : new ArrayList<>(classNode.methods)) {
            MethodNode method = (MethodNode) object;
            String id = method.name + method.desc;
            EntryTreeNode<EntryMapping> node = mappings.get(id);
            if (node != null) {
                MethodEntry entry = (MethodEntry) node.getEntry();
                String deobfuscatedName = node.getValue().getTargetName();
                if (method.name.equals(deobfuscatedName)) continue;

//                for (MethodNode methodNode : classNode.methods) {
//                    if(methodNode.name.equals(deobfuscatedName)  && methodNode.desc.equals(method.desc)){
//                        System.out.println("duplicated Method: "+id + " " +deobfuscatedName);
//
//                        break;
//                    }
//                }
//                if(duplicated) continue;

                deobfuscatedName = deobfuscatedName + "NMS";

                if (Modifier.isAbstract(method.access) || Modifier.isStatic(method.access)) continue;

                MethodVisitor methodNode = classNode.visitMethod(method.access, deobfuscatedName, method.desc, method.signature, method.exceptions.toArray(new String[0]));
                methodNode.visitCode();

                Label label1 = new Label();
                methodNode.visitLabel(label1);
                methodNode.visitVarInsn(Opcodes.ALOAD, 0);

                int index = 1;
                for (TypeDescriptor desc : entry.getDesc().getArgumentDescs()) {
                    Type type = Type.getType(desc.toString());
                    methodNode.visitVarInsn(type.getOpcode(Opcodes.ILOAD), index);
                    index=index+type.getSize();
                }

                methodNode.visitMethodInsn(Opcodes.INVOKEVIRTUAL, classNode.name, method.name, method.desc, false);

                Type returnType = Type.getType(entry.getDesc().getReturnDesc().toString());
                methodNode.visitInsn(returnType.getOpcode(Opcodes.IRETURN));

                Label label3 = new Label();
                methodNode.visitLabel(label3);
                methodNode.visitLocalVariable("this", "L" + classNode.name + ";", null, label1, label3, 0);

                index = 1;
                for (TypeDescriptor desc : entry.getDesc().getArgumentDescs()) {
                    Type type = Type.getType(desc.toString());
                    methodNode.visitLocalVariable("var"+(index-1), type.getDescriptor(), null, label1, label3, index);
                    index++;
                }
                methodNode.visitMaxs(-1, -1);
                methodNode.visitEnd();

                modifyed = true;
                System.out.println("+ " + id + " -> " + deobfuscatedName);

            }else {
                System.out.println("ignore Method: " + id);
            }
        }

        if (!modifyed) return;

        classNode.accept(classWriter);
        File outputDir = new File("test/");
        File file = new File(outputDir, ressource.getSimpleName() + ".class");
        if (file.exists()) file.delete();

        outputDir.mkdirs();

        DataOutputStream dataOutputStream =
                null;
        try {
            dataOutputStream = new DataOutputStream(
                    new FileOutputStream(
                            file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        byte[] data = classWriter.toByteArray();
        dataOutputStream.write(data);
        ressource.setData(data);

//        PrintWriter pw = new PrintWriter(System.out);
//        CheckClassAdapter.verify(new jdk.internal.org.objectweb.asm.ClassReader(classWriter.toByteArray()), true, pw);
        //return classWriter.toByteArray();
    }
}
