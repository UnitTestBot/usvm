package org.usvm.instrumentation.jacodb.transform

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.instrumentation.jacodb.util.enclosingClass
import org.usvm.instrumentation.jacodb.util.enclosingMethod

//class EncodedClass(val id: Long) {
//    val encodedMethods = hashMapOf<JcMethod, EncodedMethod>()
//    var currenMethodIndex = 0L
//}
//
//class EncodedMethod(val id: Long) {
//    val encodedInstructions = hashMapOf<JcInst, EncodedInst>()
//    var currentInstIndex = 0L
//}
//
//class EncodedInst(val id: Long)
//
//object JcInstEncoder {
//
//    private val encodedJcInstructions = hashMapOf<Long, JcInst>()
//    private val encodedClasses = hashMapOf<JcClassOrInterface, EncodedClass>()
//    private var currentClassIndex = 0L
//
//    fun encode(jcInst: JcInst): Long {
//        val jcClass = jcInst.enclosingClass
//        val jcMethod = jcInst.enclosingMethod
//        val encodedClass = encodedClasses.getOrPut(jcClass) { EncodedClass(currentClassIndex++) }
//        val encodedMethod =
//            encodedClass.encodedMethods.getOrPut(jcMethod) { EncodedMethod(encodedClass.currenMethodIndex++) }
//        val encodedInst =
//            encodedMethod.encodedInstructions.getOrPut(jcInst) { EncodedInst(encodedMethod.currentInstIndex++) }
//        val instId = (encodedClass.id shl 40) + (encodedMethod.id shl 24) + encodedInst.id
//        encodedJcInstructions[instId] = jcInst
//        return instId
//    }
//
//    fun decode(jInstructionId: Long): JcInst? = encodedJcInstructions[jInstructionId]
//
//    fun reset() {
//        encodedJcInstructions.clear()
//        encodedClasses.clear()
//        currentClassIndex = 0L
//    }
//}