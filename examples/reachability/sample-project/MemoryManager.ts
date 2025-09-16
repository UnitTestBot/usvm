// @ts-nocheck
// noinspection JSUnusedGlobalSymbols

// Memory Manager - Sample TypeScript project for reachability analysis
// This project simulates memory management operations using only SMT-solver friendly constructs:
// - Integer operations (no floating-point, no modulo)
// - Array operations (length, indexing)
// - Object field access and updates
// - Conditional logic and control flow

export enum MemoryBlockState {
    FREE = 0,
    ALLOCATED = 1,
    RESERVED = 2
}

export class MemoryBlock {
    public startAddress: number;
    public size: number;
    public state: MemoryBlockState;
    public ownerId: number;  // Process ID that owns this block

    constructor(startAddress: number, size: number) {
        this.startAddress = startAddress;
        this.size = size;
        this.state = MemoryBlockState.FREE;
        this.ownerId = 0;
    }

    public allocate(ownerId: number): boolean {
        if (this.state === MemoryBlockState.FREE) {
            this.state = MemoryBlockState.ALLOCATED;
            this.ownerId = ownerId;
            return true;
        }
        return false;
    }

    public free(): boolean {
        if (this.state === MemoryBlockState.ALLOCATED) {
            this.state = MemoryBlockState.FREE;
            this.ownerId = 0;
            return true;
        }
        return false;
    }

    public reserve(): boolean {
        if (this.state === MemoryBlockState.FREE) {
            this.state = MemoryBlockState.RESERVED;
            return true;
        }
        return false;
    }

    public getEndAddress(): number {
        return this.startAddress + this.size - 1;
    }
}

export class MemoryManager {
    private blocks: MemoryBlock[];
    private totalMemory: number;
    private allocatedMemory: number;
    private reservedMemory: number;

    constructor(totalMemory: number) {
        this.totalMemory = totalMemory;
        this.blocks = [];
        this.allocatedMemory = 0;
        this.reservedMemory = 0;

        // Initialize with one large free block
        this.blocks.push(new MemoryBlock(0, totalMemory));
    }

    public allocateMemory(size: number, ownerId: number): number {
        if (size <= 0) {
            return -1; // Invalid size
        }

        // Find suitable free block using first-fit algorithm
        for (let i = 0; i < this.blocks.length; i++) {
            const block = this.blocks[i];
            if (block.state === MemoryBlockState.FREE && block.size >= size) {
                return this.splitAndAllocate(i, size, ownerId);
            }
        }

        return -1; // No suitable block found
    }

    private splitAndAllocate(blockIndex: number, size: number, ownerId: number): number {
        const block = this.blocks[blockIndex];
        const startAddress = block.startAddress;

        if (block.size === size) {
            // Perfect fit - just allocate the whole block
            block.allocate(ownerId);
            this.allocatedMemory += size;
        } else {
            // Split the block
            const remainingSize = block.size - size;

            // Resize current block to allocated size
            block.size = size;
            block.allocate(ownerId);
            this.allocatedMemory += size;

            // Create new free block for remaining space
            const newBlock = new MemoryBlock(startAddress + size, remainingSize);
            this.insertBlock(blockIndex + 1, newBlock);
        }

        return startAddress;
    }

    private insertBlock(index: number, newBlock: MemoryBlock): void {
        // Shift elements to make room
        this.blocks.push(new MemoryBlock(0, 0)); // Add space at end
        for (let i = this.blocks.length - 1; i > index; i--) {
            this.blocks[i] = this.blocks[i - 1];
        }
        this.blocks[index] = newBlock;
    }

    public freeMemory(startAddress: number): boolean {
        // Find block by start address
        for (let i = 0; i < this.blocks.length; i++) {
            const block = this.blocks[i];
            if (block.startAddress === startAddress &&
                block.state === MemoryBlockState.ALLOCATED) {

                this.allocatedMemory -= block.size;
                block.free();

                // Try to merge with adjacent free blocks
                this.mergeAdjacentBlocks(i);
                return true;
            }
        }
        return false;
    }

    private mergeAdjacentBlocks(blockIndex: number): void {
        const block = this.blocks[blockIndex];

        // Merge with next block if it's free
        if (blockIndex + 1 < this.blocks.length) {
            const nextBlock = this.blocks[blockIndex + 1];
            if (nextBlock.state === MemoryBlockState.FREE &&
                block.getEndAddress() + 1 === nextBlock.startAddress) {

                block.size += nextBlock.size;
                this.removeBlock(blockIndex + 1);
            }
        }

        // Merge with previous block if it's free
        if (blockIndex > 0) {
            const prevBlock = this.blocks[blockIndex - 1];
            if (prevBlock.state === MemoryBlockState.FREE &&
                prevBlock.getEndAddress() + 1 === block.startAddress) {

                prevBlock.size += block.size;
                this.removeBlock(blockIndex);
            }
        }
    }

    private removeBlock(index: number): void {
        // Shift elements to remove the block
        for (let i = index; i < this.blocks.length - 1; i++) {
            this.blocks[i] = this.blocks[i + 1];
        }
        this.blocks.pop();
    }

    public getMemoryUsage(ownerId: number): number {
        let usage = 0;
        for (let i = 0; i < this.blocks.length; i++) {
            const block = this.blocks[i];
            if (block.state === MemoryBlockState.ALLOCATED && block.ownerId === ownerId) {
                usage += block.size;
            }
        }
        return usage;
    }

    public getFreeMemory(): number {
        return this.totalMemory - this.allocatedMemory - this.reservedMemory;
    }

    public getAllocatedMemory(): number {
        return this.allocatedMemory;
    }

    public getFragmentation(): number {
        // Count number of free blocks (simple fragmentation metric)
        let freeBlockCount = 0;
        for (let i = 0; i < this.blocks.length; i++) {
            if (this.blocks[i].state === MemoryBlockState.FREE) {
                freeBlockCount += 1;
            }
        }
        return freeBlockCount;
    }

    public getLargestFreeBlock(): number {
        let largest = 0;
        for (let i = 0; i < this.blocks.length; i++) {
            const block = this.blocks[i];
            if (block.state === MemoryBlockState.FREE && block.size > largest) {
                largest = block.size;
            }
        }
        return largest;
    }

    public compactMemory(): number {
        // Simple compaction - move all allocated blocks to beginning
        let compactedBlocks: MemoryBlock[] = [];
        let currentAddress = 0;
        let totalFreed = 0;

        // Collect allocated blocks and move them to beginning
        for (let i = 0; i < this.blocks.length; i++) {
            const block = this.blocks[i];
            if (block.state === MemoryBlockState.ALLOCATED) {
                const newBlock = new MemoryBlock(currentAddress, block.size);
                newBlock.allocate(block.ownerId);
                compactedBlocks.push(newBlock);
                currentAddress += block.size;
            } else if (block.state === MemoryBlockState.FREE) {
                totalFreed += block.size;
            }
        }

        // Add one large free block at the end
        if (totalFreed > 0) {
            compactedBlocks.push(new MemoryBlock(currentAddress, totalFreed));
        }

        this.blocks = compactedBlocks;
        return totalFreed;
    }

    public defragmentMemory(): boolean {
        let mergedAny = false;
        let i = 0;

        while (i < this.blocks.length - 1) {
            const current = this.blocks[i];
            const next = this.blocks[i + 1];

            if (current.state === MemoryBlockState.FREE &&
                next.state === MemoryBlockState.FREE &&
                current.getEndAddress() + 1 === next.startAddress) {

                // Merge the blocks
                current.size += next.size;
                this.removeBlock(i + 1);
                mergedAny = true;
            } else {
                i += 1;
            }
        }

        return mergedAny;
    }

    public freeAllMemoryForOwner(ownerId: number): number {
        let freedMemory = 0;

        for (let i = 0; i < this.blocks.length; i++) {
            const block = this.blocks[i];
            if (block.state === MemoryBlockState.ALLOCATED && block.ownerId === ownerId) {
                freedMemory += block.size;
                this.allocatedMemory -= block.size;
                block.free();
            }
        }

        // Perform defragmentation after freeing
        if (freedMemory > 0) {
            this.defragmentMemory();
        }

        return freedMemory;
    }
}
