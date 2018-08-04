/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dos.emulator;

/**
 *
 * @author jkim13
 */
public class CPU {

    /**
     * These are the "registers" for the CPU. These can hold whatever data we
     * want.
     */
    public int registers[];
    /**
     * The current "size" of the CPU. Right now, it should be 16 bits, but we
     * can change that.
     */
    public int size;

    /**
     * RAM data
     */
    public byte ram[];

    /**
     * Instruction pointer
     */
    public int eip;

    /**
     * Segment registers
     */
    public int cs, ds, es, fs, gs, ss;

    public final static int AX = 0;
    public final static int CX = 1;
    public final static int DX = 2;
    public final static int BX = 3;
    public final static int SP = 4;
    public final static int BP = 5;
    public final static int SI = 6;
    public final static int DI = 7;

    public CPU(int ramsize) {
        registers = new int[8];
        size = 16;

        if (ramsize < 1024 * 1024) {
            ramsize = 1024 * 1024; // 1 MB
        }
        if ((ramsize | (ramsize - 1)) != 0) {// Make sure it is a power of two
            ramsize = ramsize & ~(ramsize - 1);
        }
        ram = new byte[ramsize];
    }

    private int seg_translation(int seg, int offset) {
        return (seg << 4) + (offset & 0xFFFF);
    }

    /**
     * Read next byte.
     *
     * @return
     */
    private int next_byte() {
        int b = ram[seg_translation(cs, eip)];
        eip = (eip + 1) & 0xFFFF;
        return b & 0xFF;
    }

    /**
     * Read next word (short)
     *
     * @return
     */
    private int next_word() {
        return next_byte() | next_byte() << 8;
    }

    private int decode_modrm(int modrm) {
        switch (modrm & ~0x38) {
            case 0:
                return seg_translation(ds, registers[BX] + registers[SI]);
            case 1:
                return seg_translation(ds, registers[BX] + registers[DI]);
            case 2:
                return seg_translation(ss, registers[BP] + registers[SI]);
            case 3:
                return seg_translation(ss, registers[BP] + registers[DI]);
            case 4:
                return seg_translation(ds, registers[SI]);
            case 5:
                return seg_translation(ds, registers[DI]);
            case 6:
                return seg_translation(ds, next_word());
            case 7:
                return seg_translation(ds, registers[BX]);
            case 0x40:
                return seg_translation(ds, registers[BX] + registers[SI] + next_byte());
            case 0x41:
                return seg_translation(ds, registers[BX] + registers[DI] + next_byte());
            case 0x42:
                return seg_translation(ss, registers[BP] + registers[SI] + next_byte());
            case 0x43:
                return seg_translation(ss, registers[BP] + registers[DI] + next_byte());
            case 0x44:
                return seg_translation(ds, registers[SI] + next_byte());
            case 0x45:
                return seg_translation(ds, registers[DI] + next_byte());
            case 0x46:
                return seg_translation(ss, registers[BP] + next_byte());
            case 0x47:
                return seg_translation(ds, registers[BX] + next_byte());
            case 0x80:
                return seg_translation(ds, registers[BX] + registers[SI] + next_word());
            case 0x81:
                return seg_translation(ds, registers[BX] + registers[DI] + next_word());
            case 0x82:
                return seg_translation(ss, registers[BP] + registers[SI] + next_word());
            case 0x83:
                return seg_translation(ss, registers[BP] + registers[DI] + next_word());
            case 0x84:
                return seg_translation(ds, registers[SI] + next_word());
            case 0x85:
                return seg_translation(ds, registers[DI] + next_word());
            case 0x86:
                return seg_translation(ss, registers[BP] + next_word());
            case 0x87:
                return seg_translation(ds, registers[BX] + next_word());
            default:
                throw new IllegalStateException("Unknown ModR/M value: " + Integer.toHexString(modrm & ~0x38));
        }
    }
    
    public int read_rm8(int modrm){
        if(modrm < 0xC0){
            return ram[decode_modrm(modrm)];
        }else return registers[modrm & 7];
    }
    public int read_rm16(int modrm){
        if(modrm < 0xC0){
            return ram[decode_modrm(modrm)];
        }else return registers[modrm & 7];
    }

    // Main loop
    public void run() {
        while (true) {
            int opcode = next_byte();
            int modrm = 0, op1, op2;
            switch (opcode) {
                case 0x00:
                    modrm=next_byte();
                case 0x01:
                case 0x02:
                case 0x03:
                case 0x04:
                case 0x05: // ADD
                case 0x08:
                case 0x09:
                case 0x0A:
                case 0x0B:
                case 0x0C:
                case 0x0D: // OR
                case 0x10:
                case 0x11:
                case 0x12:
                case 0x13:
                case 0x14:
                case 0x15: // ADC
                case 0x18:
                case 0x19:
                case 0x1A:
                case 0x1B:
                case 0x1C:
                case 0x1D: // SBB
                case 0x20:
                case 0x21:
                case 0x22:
                case 0x23:
                case 0x24:
                case 0x25: // AND
                case 0x28:
                case 0x29:
                case 0x2A:
                case 0x2B:
                case 0x2C:
                case 0x2D: // SUB
                case 0x30:
                case 0x31:
                case 0x32:
                case 0x33:
                case 0x34:
                case 0x35: // XOR
                case 0x38:
                case 0x39:
                case 0x3A:
                case 0x3B:
                case 0x3C:
                case 0x3D: // CMP
                case 0x90: // NOP
                default:
                    throw new UnsupportedOperationException(String.format("Opcode %02x not found!", opcode));
            }
        }
    }
}
