package org.usvm.samples

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.usvm.annotations.ids.SlotId
import org.usvm.annotations.ids.setSlotBit
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.interpreters.concrete.utils.VirtualPythonObject

class VirtualObjectsTest {
    companion object {
        val slotMethods: Map<SlotId, (PyObject) -> Boolean> = mapOf(
            SlotId.NbBool to ConcretePythonInterpreter.typeHasNbBool,
            SlotId.NbAdd to ConcretePythonInterpreter.typeHasNbAdd,
            SlotId.NbSubtract to ConcretePythonInterpreter.typeHasNbSubtract,
            SlotId.NbMultiply to ConcretePythonInterpreter.typeHasNbMultiply,
            SlotId.NbMatrixMultiply to ConcretePythonInterpreter.typeHasNbMatrixMultiply,
            SlotId.NbNegative to ConcretePythonInterpreter.typeHasNbNegative,
            SlotId.NbPositive to ConcretePythonInterpreter.typeHasNbPositive,
            SlotId.SqLength to ConcretePythonInterpreter.typeHasSqLength,
            SlotId.SqConcat to ConcretePythonInterpreter.typeHasSqConcat,
            SlotId.MpSubscript to ConcretePythonInterpreter.typeHasMpSubscript,
            SlotId.MpAssSubscript to ConcretePythonInterpreter.typeHasMpAssSubscript,
            SlotId.TpRichcompare to ConcretePythonInterpreter.typeHasTpRichcmp,
            SlotId.TpGetattro to ConcretePythonInterpreter.typeHasTpGetattro,
            SlotId.TpSetattro to ConcretePythonInterpreter.typeHasTpSetattro,
            SlotId.TpIter to ConcretePythonInterpreter.typeHasTpIter,
            SlotId.TpCall to ConcretePythonInterpreter.typeHasTpCall,
            SlotId.TpHash to ConcretePythonInterpreter.typeHasTpHash
        )
    }

    fun checkSlotDisabling(
        slotId: SlotId,
    ): Boolean? {
        val obj = VirtualPythonObject(-1 - slotId.ordinal)
        obj.slotMask.setSlotBit(slotId, false)
        val pyObj = ConcretePythonInterpreter.allocateVirtualObject(obj)
        val type = ConcretePythonInterpreter.getPythonObjectType(pyObj)
        val method = slotMethods[slotId] ?: return null
        return !method(type)
    }

    @Test
    fun testAllEnabled() {
        val obj = VirtualPythonObject(-1)
        val pyObj = ConcretePythonInterpreter.allocateVirtualObject(obj)
        val type = ConcretePythonInterpreter.getPythonObjectType(pyObj)
        var result = true
        for (method in slotMethods.values) {
            if (!method(type)) {
                result = false
                break
            }
        }
        assertTrue(result)
    }

    @Test
    fun testNbBoolDisabled() {
        val result = checkSlotDisabling(SlotId.NbBool) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testNbAddDisabled() {
        val result = checkSlotDisabling(SlotId.NbAdd) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testNbSubtractDisabled() {
        val result = checkSlotDisabling(SlotId.NbSubtract) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testNbMultiplyDisabled() {
        val result = checkSlotDisabling(SlotId.NbMultiply) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testNbMatrixMultiplyDisabled() {
        val result = checkSlotDisabling(SlotId.NbMatrixMultiply) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testNbNegativeDisabled() {
        val result = checkSlotDisabling(SlotId.NbNegative) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testNbPositiveDisabled() {
        val result = checkSlotDisabling(SlotId.NbPositive) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testSqLengthDisabled() {
        val result = checkSlotDisabling(SlotId.SqLength) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testSqConcatDisabled() {
        val result = checkSlotDisabling(SlotId.SqConcat) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testMpSubscriptDisabled() {
        val result = checkSlotDisabling(SlotId.MpSubscript) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testMpAssSubscriptDisabled() {
        val result = checkSlotDisabling(SlotId.MpAssSubscript) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testTpRichcompareDisabled() {
        /*
         * Check that attempt to disable tp_hash does not
         * interfere with disabling tp_richcompare.
         */ 
        val obj = VirtualPythonObject(-1 - SlotId.TpRichcompare.ordinal)
        obj.slotMask.setSlotBit(SlotId.TpRichcompare, false).setSlotBit(SlotId.TpHash, false)
        val pyObj = ConcretePythonInterpreter.allocateVirtualObject(obj)
        val type = ConcretePythonInterpreter.getPythonObjectType(pyObj)
        val checkTpRichCompare = slotMethods[SlotId.TpRichcompare] ?: return
        val checkTpHash = slotMethods[SlotId.TpHash] ?: return
        assertFalse(checkTpRichCompare(type))
        assertTrue(checkTpHash(type)) // hash cannot be disabled
    }
    
    @Test
    fun testTpGetattroDisabled() {
        val result = checkSlotDisabling(SlotId.TpGetattro) ?: return
        assertFalse(result) // tp_getattro is marked as mandatory
    }
    
    @Test
    fun testTpSetattroDisabled() {
        val result = checkSlotDisabling(SlotId.TpSetattro) ?: return
        assertFalse(result) // tp_setattro is marked as mandatory
    }
    
    @Test
    fun testTpIterDisabled() {
        val result = checkSlotDisabling(SlotId.TpIter) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testTpCallDisabled() {
        val result = checkSlotDisabling(SlotId.TpCall) ?: return
        assertTrue(result)
    }
    
    @Test
    fun testTpHashDisabled() {
        val result = checkSlotDisabling(SlotId.TpHash) ?: return
        assertFalse(result) // tp_hash is marked as mandatory
    }
}