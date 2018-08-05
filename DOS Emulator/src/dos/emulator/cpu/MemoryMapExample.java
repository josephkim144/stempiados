/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dos.emulator.cpu;

/**
 *
 * @author jkim13
 */
public class MemoryMapExample {

    CPU cpu;

    public void test() {
        cpu.add_memory_map(new MemoryMap() {
            public void handler(int addr, int data) {
                System.out.printf("Wrote byte at %x: %02x", addr, data);
            }
        }, 4096);

    }
}
