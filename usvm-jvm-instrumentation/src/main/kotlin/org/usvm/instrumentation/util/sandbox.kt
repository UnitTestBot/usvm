//package org.usvm.instrumentation.util
//
//import sun.security.provider.PolicyFile
//import java.nio.file.Files
//import java.nio.file.Paths
//import java.security.*
//import java.security.cert.Certificate
//
//object SecurityManager {
//    private val policyPath = Paths.get("./usvm.policy")
//
//    private val permissions = Permissions()
//    private val allCodeSource = CodeSource(null, emptyArray<Certificate>())
//
//    init {
//        permissions.add(RuntimePermission("accessDeclaredMembers"))
//        permissions.add(RuntimePermission("getStackWalkerWithClassReference"))
//        permissions.add(RuntimePermission("getClassLoader"))
//
//        if (Files.exists(policyPath)) {
//            val policyFile = PolicyFile(policyPath.toUri().toURL())
//            policyFile.getPermissions(allCodeSource).elements().toList().forEach { permissions.add(it) }
//        }
//    }
//
////    fun <T> sandbox(block: () -> T): T {
////        val acc = AccessControlContext(arrayOf(ProtectionDomain(allCodeSource, permissions)))
////        return try {
////            AccessController.doPrivileged(PrivilegedAction { block() }, acc)
////        } catch (e: PrivilegedActionException) {
////            throw e.exception
////        }
////    }
//
//}
