package org.usvm.ts.calls

/** Used inside the existing native replay wrapper with its lossless primitive decoder and exact prototype registry. */
internal fun callsConcreteHeapRestoreSource(): String = """
    function restoreCallsConcreteHeap(graph: any, prototypes: Map<string, object>, decodePrimitive: (x: any) => any,
                                     writeBinding?: (field: any, value: any) => void): any {
      const objects = new Map<number, any>();
      for (const node of graph.objects) {
        if (objects.has(node.id)) throw Error('Duplicate concrete object ID');
        let value: any;
        if (node.elements != null) {
          value = new Array(node.elements.length);
        } else {
          const prototype = node.classSignature == null ? Object.prototype : prototypes.get(node.classSignature);
          if (prototype == null) throw Error('Exact concrete class prototype is unavailable: ' + node.classSignature);
          value = Object.create(prototype);
        }
        objects.set(node.id, value);
      }
      function resolve(slot: any): any {
        if (slot.objectId != null) {
          if (!objects.has(slot.objectId)) throw Error('Missing concrete object reference');
          return objects.get(slot.objectId);
        }
        return decodePrimitive(slot.primitive);
      }
      for (const node of graph.objects) {
        const target = objects.get(node.id);
        const entries = node.elements != null ? node.elements.map((value: any, index: number) => [String(index), value]) :
                                               Object.entries(node.fields);
        for (const [key, value] of entries) {
          Object.defineProperty(target, key, { value: resolve(value), writable: true, enumerable: true, configurable: true });
        }
      }
      for (const field of graph.environmentFields ?? []) {
        if (writeBinding == null) throw Error('Initial environment binding setter is unavailable');
        writeBinding(field, resolve(field.value));
      }
      return { receiver: graph.receiver == null ? undefined : resolve(graph.receiver),
               arguments: graph.arguments.map(resolve), objects };
    }
""".trimIndent()
