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
public class FPU {
    public double[] stack = new double[8];
    
    public int ftop;
    
    public boolean c0, c1, c2, c3;
    
    CPU cpu;
    
    public FPU(CPU cpu){
        ftop = 0;
        this.cpu=cpu;
    }
    
    private void fpush(double d){
        stack[ftop] = d;
        ftop = (ftop - 1) & 7;
    }
    
    public void op(int opcode, int modrm){
        switch(opcode){
            case 0xD8:
                switch(modrm >> 3 & 7){
                    case 0: // FADD
                }
        }
    }
}
