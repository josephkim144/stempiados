/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dos.emulator.cpu;

import dos.emulator.IO;

// TODO (bug fixes):
//  - Make sure that callf is implemented correctly (doesn't use next_byte while updating cs/ip)
//  - Check for missing returns
//  - Make sure that rb/rw/wb/ww are not used when segment registers can be modified
//  - Make sure that every case has either a return or a continue
//  - Look over string instructions
// TODO (features):
//  - 80186 opcodes
//  - FPU (64-bit only)
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
    public final static int AL = 0;
    public final static int CL = 1;
    public final static int DL = 2;
    public final static int BL = 3;
    public final static int AH = 4;
    public final static int CH = 5;
    public final static int DH = 6;
    public final static int BH = 7;
    public final static int ES = 0;
    public final static int CS = 1;
    public final static int SS = 2;
    public final static int DS = 3;
    public final static int FS = 4;
    public final static int GS = 5;

    public final static int NO_REP_PREFIX = 0;
    public final static int REPZ = 1;
    public final static int REPNZ = 2;

    boolean of, sf, zf, af, pf, cf;
    int additional_eflags_bits = 2;

    /**
     * Sets processor to 8086 or 80186.
     *
     * Differences between 8086/80186: - 8086 pushes SP AFTER it has been
     * decremented, 80186 does not - 8086 does NOT mask shifts - When writing a
     * word to 0xFFFF, 80186 incorrectly writes to offset 0x10000, rather than 0
     * as in 8086.
     */
    public int architecture = 8086;

    /**
     * Enables x87 FPU. Note: Only supports 64-bit doubles. Not implemented yet
     */
    public int fpu_enabled = 0;
    public FPU fpu;

    /**
     * Holds memory mappings
     */
    MemoryMap[] memory_maps;

    public CPU(int ramsize) {
        registers = new int[8];
        size = 16;

        if (ramsize < 1024 * 1024) {
            ramsize = 1024 * 1024; // 1 MB
        }
        if ((ramsize | (ramsize - 1)) != 0) { // Make sure it is a power of two
            ramsize = ramsize & ~(ramsize - 1);
        }
        ram = new byte[ramsize];

        memory_maps = new MemoryMap[256];

        fpu = new FPU(this);
    }

    public void set_architecture(int cpu) {
        architecture = cpu;
    }

    public int get_architecture() {
        return architecture;
    }

    public void add_memory_map(MemoryMap a, int address) {
        memory_maps[address >> 12] = a;
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

    /**
     * Segment register offset
     */
    private int current_sreg = -1;
    /**
     * REP prefix
     */
    private int rep = 0;

    // Internal functions to write a word/byte
    private void wb(int seg, int offset, int value) {
        write_byte(seg_translation(seg, offset), value);
    }

    private void ww(int seg, int offset, int value) {
        if (architecture == 8086) { // We need to wrap around
            write_byte(seg_translation(seg, offset), value);
            write_byte(seg_translation(seg, offset + 1), value >> 8);
        } else {
            int phys = seg_translation(seg, offset);
            write_byte(phys, value);
            write_byte(phys + 1, value >> 8);
        }
    }

    // Reading
    private int rb(int seg, int offset) {
        return read_byte(seg_translation(seg, offset));
    }

    private int rw(int seg, int offset) {
        if (architecture == 8086) {
            return read_word(seg_translation(seg, offset));
        } else {
            int phys = seg_translation(seg, offset);
            return read_word(phys);
        }
    }

    private int $cseg;

    private int seg_translation_internal(int sreg, int addr) {
        if (current_sreg != -1) {
            sreg = current_sreg;
            current_sreg = -1;
        }
        $cseg = sreg;
        return addr;//seg_translation(sreg, addr);
    }

    private int decode_modrm(int modrm) {
        return decode_modrm(modrm, 0);
    }

    private int decode_modrm(int modrm, int offset) {
        switch (modrm & ~0x38) {
            case 0:
                return seg_translation_internal(ds, registers[BX] + registers[SI] + offset);
            case 1:
                return seg_translation_internal(ds, registers[BX] + registers[DI] + offset);
            case 2:
                return seg_translation_internal(ss, registers[BP] + registers[SI] + offset);
            case 3:
                return seg_translation_internal(ss, registers[BP] + registers[DI] + offset);
            case 4:
                return seg_translation_internal(ds, registers[SI] + offset);
            case 5:
                return seg_translation_internal(ds, registers[DI] + offset);
            case 6:
                return seg_translation_internal(ds, next_word() + offset);
            case 7:
                return seg_translation_internal(ds, registers[BX] + offset);
            case 0x40:
                return seg_translation_internal(ds, registers[BX] + registers[SI] + next_byte() + offset);
            case 0x41:
                return seg_translation_internal(ds, registers[BX] + registers[DI] + next_byte() + offset);
            case 0x42:
                return seg_translation_internal(ss, registers[BP] + registers[SI] + next_byte() + offset);
            case 0x43:
                return seg_translation_internal(ss, registers[BP] + registers[DI] + next_byte() + offset);
            case 0x44:
                return seg_translation_internal(ds, registers[SI] + next_byte() + offset);
            case 0x45:
                return seg_translation_internal(ds, registers[DI] + next_byte() + offset);
            case 0x46:
                return seg_translation_internal(ss, registers[BP] + next_byte() + offset);
            case 0x47:
                return seg_translation_internal(ds, registers[BX] + next_byte() + offset);
            case 0x80:
                return seg_translation_internal(ds, registers[BX] + registers[SI] + next_word() + offset);
            case 0x81:
                return seg_translation_internal(ds, registers[BX] + registers[DI] + next_word() + offset);
            case 0x82:
                return seg_translation_internal(ss, registers[BP] + registers[SI] + next_word() + offset);
            case 0x83:
                return seg_translation_internal(ss, registers[BP] + registers[DI] + next_word() + offset);
            case 0x84:
                return seg_translation_internal(ds, registers[SI] + next_word() + offset);
            case 0x85:
                return seg_translation_internal(ds, registers[DI] + next_word() + offset);
            case 0x86:
                return seg_translation_internal(ss, registers[BP] + next_word() + offset);
            case 0x87:
                return seg_translation_internal(ds, registers[BX] + next_word() + offset);
            default:
                throw new IllegalStateException("Unknown ModR/M value: " + Integer.toHexString(modrm & ~0x38));
        }
    }

    /**
     * Function to read byte from memory
     *
     * @param addr
     * @return
     */
    public int read_byte(int addr) {
        return ram[addr & 0xFFFFF] & 0xFF;
    }

    /**
     * Function to read word (aka short) from memory.
     *
     * @param addr
     * @return
     */
    public int read_word(int addr) {
        return read_byte(addr) | read_byte(addr + 1) << 8;
    }

    /**
     * Write byte to memory
     *
     * @param addr
     * @param value
     */
    public void write_byte(int addr, int value) {
        if (memory_maps[addr >> 12] != null) {
            memory_maps[addr >> 12].handler(addr, value);
        }
        ram[addr & 0xFFFFF] = (byte) (value & 0xFF);
    }

    /**
     * Write word to memory
     *
     * @param addr
     * @param value
     */
    public void write_word(int addr, int value) {
        write_byte(addr, value & 0xFF);
        write_byte(addr + 1, value >> 8 & 0xFF);
    }

    /**
     * Read and operand pointed to by an r/m8.
     *
     * @param modrm
     * @return
     */
    public int read_rm8(int modrm) {
        if (modrm < 0xC0) {
            int dm = decode_modrm(modrm);
            $global_dm = dm;
            return rb($cseg, dm);
        } else {
            return get_reg8(modrm & 7);
        }
    }

    /**
     * Write an operand pointed to by an r/m8
     *
     * @param modrm
     * @param value
     */
    public void write_rm8(int modrm, int value) {
        if (modrm < 0xC0) {
            int dm = decode_modrm(modrm);
            $global_dm = dm;
            System.out.printf("[%02x] Current sreg: %04x, offset: %04x, makes: %08x\n", modrm, $cseg, dm, ($cseg << 4) + dm);
            wb($cseg, dm, value);
        } else {
            set_reg8(modrm & 7, value);
        }
    }

    public int read_reg8(int modrm) {
        return get_reg8(modrm >> 3 & 7);
    }

    public void write_reg8(int modrm, int value) {
        set_reg8(modrm >> 3 & 7, value);
    }

    
    int $global_dm = 0;
    public void write_rm8_IMM(int modrm, int value){
        if(modrm < 0xC0){
            wb($cseg, $global_dm, value);
        }else{
            write_rm16(modrm, value);
        }
    }
    public void write_rm16_IMM(int modrm, int value){
        if(modrm < 0xC0){
            ww($cseg, $global_dm, value);
        }else{
            write_rm16(modrm, value);
        }
    }
    /**
     * Read and operand pointed to by an r/m8.
     *
     * @param modrm
     * @return
     */
    public int read_rm16(int modrm) {
        if (modrm < 0xC0) {
            int dm = decode_modrm(modrm);
            $global_dm = dm;
            return rw($cseg, dm);
        } else {
            return get_reg16(modrm & 7);
        }
    }

    /**
     * Write an operand pointed to by an r/m8
     *
     * @param modrm
     * @param value
     */
    public void write_rm16(int modrm, int value) {
        if (modrm < 0xC0) {
            int dm = decode_modrm(modrm);
            $global_dm = dm;
            ww($cseg, dm, value);
        } else {
            set_reg16(modrm & 7, value);
        }
    }

    public int read_reg16(int modrm) {
        return get_reg16(modrm >> 3 & 7);
    }

    public void write_reg16(int modrm, int value) {
        set_reg16(modrm >> 3 & 7, value);
    }

    /**
     * Get 8-bit register. The algorithm is a little bit complicated, but here
     * is the gist of it:
     *
     * To get the *register number*, we simply do "& 3". This separates out the
     * "ABCD" from the "LH." Then, we get the fourth bit (and only the fourth
     * bit). The two possible values from it are 0 and 4. If we have an "L"
     * register, then we do not have to shift, but if we do, we have to shift it
     * down. Therefore, we shift left by 1, multiplying the bit by 2. Thus, this
     * value will always be 0 (if AL, etc.) or 8 (AH, etc.). Finally, we
     * truncate the byte.
     *
     * @param id
     *
     * @return
     */
    public int get_reg8(int id) {
        return (registers[id & 3] >>> ((id & 4) << 1)) & 0xFF;
    }

    public void set_reg8(int id, int value) {
        int shift_constant = ((id & 4) << 1);
        registers[id & 3] &= ~(0xFF << shift_constant); // Clear out the byte
        registers[id & 3] |= (value & 0xFF) << shift_constant;
    }

    /**
     * Get 16-bit register.
     *
     * @param id
     * @return
     */
    public int get_reg16(int id) {
        return registers[id] & 0xFFFF;
    }

    public void set_reg16(int id, int value) {
        registers[id] = value & 0xFFFF;
    }

    private int[] parity_table = {0, 0 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1, 0, 1, 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1, 1, 1, 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1 ^ 1, 1 ^ 1, 1, 1 ^ 1, 1 ^ 1, 1, 0, 0 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1 ^ 1, 0 ^ 1, 0, 0 ^ 1, 0 ^ 1, 0};

    private void set_zf_pf_sf(int res, int size) {
        int SIGN_BIT = 1 << (size - 1);
        zf = res == 0;
        pf = parity_table[res & 0xFF] == 0;
        sf = (res & SIGN_BIT) != 0;
    }

    private void set_add_flags(int size, int left, int right, int res) {
        int SIGN_BIT = 1 << (size - 1);
        cf = (left & (1 << size)) != 0;
        af = (((left ^ right) ^ res) & 0x10) != 0;
        of = (((left ^ right ^ SIGN_BIT) & (res ^ right)) & SIGN_BIT) != 0;
        set_zf_pf_sf(res, size);
    }

    private void set_sub_flags(int size, int left, int right, int res) {
        int SIGN_BIT = 1 << (size - 1);
        cf = (res & (1 << size)) != 0;
        af = (((left ^ right) ^ res) & 0x10) != 0;
        of = (((left ^ right) & (res ^ left)) & SIGN_BIT) != 0;
        set_zf_pf_sf(res, size);
    }

    private void set_bit_flags(int size, int left, int right, int res) {
        cf = af = of = false;
        set_zf_pf_sf(res, size);
    }

    public void push16(int data) {
        registers[SP] = (registers[SP] - 2) & 0xFFFF;
        write_word(seg_translation(ss, registers[SP]), data);
    }

    public int pop16() {
        int b = read_word(seg_translation(ss, registers[SP]));
        registers[SP] = (registers[SP] + 2) & 0xFFFF;
        return b;
    }

    public static final int CF = (1 << 0);
    public static final int PF = (1 << 2);
    public static final int AF = (1 << 4);
    public static final int ZF = (1 << 6);
    public static final int SF = (1 << 7);
    public static final int DF = (1 << 10);
    public static final int OF = (1 << 11);

    public int get_eflags() {
        int val = this.additional_eflags_bits;
        if (of) {
            val |= OF;
        }
        if (sf) {
            val |= SF;
        }
        if (zf) {
            val |= ZF;
        }
        if (af) {
            val |= AF;
        }
        if (pf) {
            val |= PF;
        }
        if (cf) {
            val |= CF;
        }
        return val;
    }

    public void set_eflags(int c) {
        of = (c & OF) != 0;
        sf = (c & SF) != 0;
        zf = (c & ZF) != 0;
        af = (c & AF) != 0;
        pf = (c & PF) != 0;
        cf = (c & CF) != 0;
        this.additional_eflags_bits = c & ~(OF | SF | ZF | AF | PF | CF);
    }

    int instructions = 0;

    // Main loop
    public void run() {
        instructions++;
        current_sreg = -1;
        rep = 0;

        System.out.printf("(%d) %04x:%04x -> %02x\n", instructions, cs, eip, this.rb(cs, eip));
        
        run_instruction();
        dump_state();
    }
    
    public void dump_state(){
        System.out.printf("AX: %04x CX: %04x DX: %04x BX: %04x\n", registers[0],registers[1],registers[2],registers[3]);
        System.out.printf("SP: %04x BP: %04x SI: %04x DI: %04x\n", registers[4],registers[5],registers[6],registers[7]);
    }

    public void reset() {
        eip = 0xFFF0;
        cs = 0xF000;
        this.additional_eflags_bits = 2;
    }

    /**
     * Call interrupt vector!
     *
     * @param num
     */
    public void interrupt(int num) {
        push16(get_eflags());
        push16(cs);
        push16(eip);
        eip = read_word((num << 2) + 0);
        cs = read_word((num << 2) + 2);
        this.additional_eflags_bits &= 0xFCFF;
    }

    protected void step() {
        run_instruction();
    }

    public int run_arith(int size, int instruction, int op1, int op2) {
        int res = 0;
        switch (instruction) {
            // ADD: Adds two numbers together
            case 0:
                res = op1 + op2;
                set_add_flags(size, op1, op2, res);
                break;
            // SUB: Subtracts two numbers
            case 1:
                res = op1 | op2;
                set_bit_flags(size, op1, op2, res);
                break;
            // ADC: Adds two numbers, plus the carry flag
            case 2:
                res = (cf ? 1 : 0) + (op1 + op2);
                set_add_flags(size, op1, op2, res);
                break;
            // SBB: Subtracts two numbers, and then the carry flag
            case 3:
                res = (op1 - op2) - (cf ? 1 : 0);
                set_sub_flags(size, op1, op2, res);
                break;
            // AND: Bitwise AND
            case 4:
                res = op1 & op2;
                set_bit_flags(size, op1, op2, res);
                break;
            // SUB: Subtract two numbers
            case 5:
            case 7:
                res = op1 - op2;
                set_bit_flags(size, op1, op2, res);
                break;
            // XOR: Bitwise XOR
            case 6:
                res = op1 ^ op2;
                set_bit_flags(size, op1, op2, res);
                break;
        }
        return res;
    }

    int read_rm(int size, int modrm) {
        if (size == 8) {
            return read_rm8(modrm);
        } else {
            return read_rm16(modrm);
        }
    }

    int read_reg(int size, int modrm) {
        if (size == 8) {
            return read_reg8(modrm);
        } else {
            return read_reg16(modrm);
        }
    }

    int nextv(int opsz) {
        if (opsz == 8) {
            return next_byte();
        } else {
            return next_word();
        }
    }

    void write_rm(int size, int modrm, int value) {
        if (size == 8) {
            write_rm8(modrm, value);
        } else {
            this.write_rm16(modrm, value);
        }
    }

    void write_reg(int size, int modrm, int value) {
        if (size == 8) {
            write_reg8(modrm, value);
        } else {
            this.write_reg16(modrm, value);
        }
    }

    private void run_instruction() {
        while (true) {
            int opcode = next_byte();
            int modrm = 0, op1, op2, res;
            switch (opcode) {
                case 0x00:
                case 0x01:
                case 0x02:
                case 0x03:
                case 0x04:
                case 0x05:
                case 0x08:
                case 0x09:
                case 0x0A:
                case 0x0B:
                case 0x0C:
                case 0x0D:
                case 0x10:
                case 0x11:
                case 0x12:
                case 0x13:
                case 0x14:
                case 0x15:
                case 0x18:
                case 0x19:
                case 0x1A:
                case 0x1B:
                case 0x1C:
                case 0x1D:
                case 0x20:
                case 0x21:
                case 0x22:
                case 0x23:
                case 0x24:
                case 0x25:
                case 0x28:
                case 0x29:
                case 0x2A:
                case 0x2B:
                case 0x2C:
                case 0x2D:
                case 0x30:
                case 0x31:
                case 0x32:
                case 0x33:
                case 0x34:
                case 0x35:
                case 0x38:
                case 0x39:
                case 0x3A:
                case 0x3B:
                case 0x3C:
                case 0x3D:
                    modrm = next_byte();
                    int opsz = 8 << (opcode & 1);
                    int opc = opcode >> 3;
                    switch (opcode >> 1 & 3) {
                        // 0 and 1: r/m, r
                        case 0:
                            op1 = read_rm(opsz, modrm);
                            op2 = read_reg(opsz, modrm);
                            break;
                        // 2 and 3: r, r/m
                        case 1:
                            op1 = read_reg(opsz, modrm);
                            op2 = read_rm(opsz, modrm);
                            break;
                        // 4 and 5: al, ib
                        case 2:
                            op1 = read_reg(opsz, 0xC0);
                            op2 = nextv(opsz);
                            break;
                        // 6 and 7: Invalid
                        default:
                            throw new Error("Unexpected opcode: " + opcode);
                    }
                    res = run_arith(opsz, opc, op1, op2);
                    if (opc != 7) {
                        switch (opcode >> 1 & 3) {
                            case 0:
                                write_rm(opsz, modrm, res);
                                break;
                            case 1:
                                write_reg(opsz, modrm, res);
                                break;
                            case 2:
                                write_rm(opsz, 0xC0, res);
                                break;
                        }
                    }
                    return;
                case 0x06:
                    push16(es);
                    return;
                case 0x07:
                    es = pop16();
                    return;
                case 0x0E:
                    push16(cs);
                    return;
                case 0x0F:
                    cs = pop16();
                    return;
                case 0x16:
                    push16(ss);
                    return;
                case 0x17:
                    ss = pop16();
                    return;
                case 0x1E:
                    push16(ds);
                    return;
                case 0x1F:
                    ds = pop16();
                    return;
                case 0x26:
                    current_sreg = es;
                    continue;
                case 0x27: {
                    // https://www.felixcloutier.com/x86/DAA.html
                    int old_al = get_reg8(AL);
                    int al = old_al;
                    boolean old_cf = cf;
                    cf = false;
                    if (((al & 15) > 9) || af) {
                        al += 6;
                        cf = old_cf || (al > 255); // al > 255 is carry out
                        al &= 0xFF;
                        af = true;
                    } else {
                        af = false;
                    }
                    if ((old_al > 0x99) || old_cf) {
                        al = (al + 0x60) & 0xFF;
                        cf = true;
                    } else {
                        cf = false;
                    }
                    set_reg8(AL, al);
                    this.set_zf_pf_sf(al, 8);
                    of = false; // ?
                    return;
                }
                case 0x2E:
                    current_sreg = cs;
                    continue;
                case 0x2F: {// https://www.felixcloutier.com/x86/DAS.html
                    int old_al = get_reg8(AL);
                    int al = old_al;
                    boolean old_cf = cf;

                    cf = false;
                    if (((al & 15) > 9) || af) {
                        al -= 6;
                        cf = old_cf || (al < 0); // al < 0 is carry out
                        al &= 0xFF;
                        af = true;
                    } else {
                        af = false;
                    }
                    if ((old_al > 0x99) || old_cf) {
                        al = (al - 0x60) & 0xFF;
                        cf = true;
                    } else {
                        cf = false;
                    }
                    this.set_zf_pf_sf(al, 8);
                    of = false; // ?
                    set_reg8(AL, al);
                    return;
                }
                case 0x36:
                    current_sreg = ss;
                    continue;
                case 0x37:
                    // https://www.felixcloutier.com/x86/AAA.html
                    boolean dddd = af;
                    if (((get_reg8(AL) & 15) > 9) || af) {
                        if (this.architecture == 8086) {
                            set_reg8(AL, get_reg8(AL) + 6);
                            set_reg8(AH, get_reg8(AH) + 1);
                        } else {
                            registers[AX] = (registers[AX] + 0x106) & 0xFFFF;
                        }
                        af = true;
                        cf = true;

                        // According to native?
                        zf = false;
                        sf = false;
                    } else {
                        af = false;
                        cf = false;
                        // I think?
                        this.set_zf_pf_sf(registers[AX], 16);
                    }
                    of = false; // according to native
                    set_reg8(AL, 0x0F & get_reg8(AL));
                    return;
                case 0x3E:
                    current_sreg = ds;
                    continue;
                case 0x3F:
                    // https://www.felixcloutier.com/x86/AAS.html
                    if (((get_reg8(AL) & 15) > 9) || af) {
                        registers[AX] = (registers[AX] - 0x106) & 0xFFFF;
                        set_reg8(AH, get_reg8(AH) - 1);
                        af = true;
                        cf = true;
                        set_reg8(AL, 0x0F & get_reg8(AL));
                    } else {
                        af = false;
                        cf = false;
                        set_reg8(AL, 0x0F & get_reg8(AL));
                    }
                    return;
                case 0x40:
                case 0x41:
                case 0x42:
                case 0x43:
                case 0x44:
                case 0x45:
                case 0x46:
                case 0x47: {
                    boolean saved_cf = cf;
                    op1 = registers[opcode & 7];
                    res = (op1 + 1);
                    set_add_flags(16, op1, 1, res);
                    cf = saved_cf;
                    registers[opcode & 7] = res & 0xFFFF;
                    return;
                }
                case 0x48:
                case 0x49:
                case 0x4A:
                case 0x4B:
                case 0x4C:
                case 0x4D:
                case 0x4E:
                case 0x4F: {
                    boolean saved_cf = cf;
                    op1 = registers[opcode & 7];
                    res = (op1 - 1);
                    set_sub_flags(16, op1, 1, res);
                    cf = saved_cf;
                    registers[opcode & 7] = res & 0xFFFF;
                    return;
                }
                case 0x50:
                case 0x51:
                case 0x52:
                case 0x53:
                //case 0x54:
                case 0x55:
                case 0x56:
                case 0x57:
                    push16(registers[opcode & 7]);
                    return;
                case 0x54: // PUSH SP
                    if (architecture == 8086) {
                        registers[SP] = (registers[SP] - 2) & 0xFFFF;
                        ww(ss, registers[SP], registers[SP]); // Pushes post sp-modified ESP
                    } else {
                        push16(registers[SP]);
                    }
                    return;
                case 0x58:
                case 0x59:
                case 0x5A:
                case 0x5B:
                case 0x5C:
                case 0x5D:
                case 0x5E:
                case 0x5F:
                    registers[opcode & 7] = pop16();
                    return;
                case 0x60:
                case 0x70: // JO
                    op1 = (byte) next_byte(); // -127 ... 128
                    if (of) {
                        eip += op1;
                    }
                    return;
                case 0x61:
                case 0x71: // JNO
                    op1 = (byte) next_byte();
                    if (!of) {
                        eip += op1;
                    }
                    return;
                case 0x62:
                case 0x72: // JC
                    op1 = (byte) next_byte();
                    if (cf) {
                        eip += op1;
                    }
                    return;
                case 0x63:
                case 0x73: // JNC
                    op1 = (byte) next_byte();
                    if (cf) {
                        eip += op1;
                    }
                    return;
                case 0x64:
                case 0x74: // JZ
                    op1 = (byte) next_byte();
                    if (zf) {
                        eip += op1;
                    }
                    return;
                case 0x65:
                case 0x75: // JNZ
                    op1 = (byte) next_byte();
                    if (!zf) {
                        eip += op1;
                    }
                    return;
                case 0x66:
                case 0x76: // JBE
                    op1 = (byte) next_byte();
                    if (cf || zf) {
                        eip += op1;
                    }
                    return;
                case 0x67:
                case 0x77: // JNBE
                    op1 = (byte) next_byte();
                    if (!cf && !zf) {
                        eip += op1;
                    }
                    return;
                case 0x68:
                case 0x78: // JS
                    op1 = (byte) next_byte();
                    if (sf) {
                        eip += op1;
                    }
                    return;
                case 0x69:
                case 0x79: // JNS
                    op1 = (byte) next_byte();
                    if (!sf) {
                        eip += op1;
                    }
                    return;
                case 0x6A:
                case 0x7A: // JP
                    op1 = (byte) next_byte();
                    if (pf) {
                        eip += op1;
                    }
                    return;
                case 0x6B:
                case 0x7B: // JNP
                    op1 = (byte) next_byte();
                    if (!pf) {
                        eip += op1;
                    }
                    return;
                case 0x6C:
                case 0x7C: // JL
                    op1 = (byte) next_byte();
                    if (sf != of) {
                        eip += op1;
                    }
                    return;
                case 0x6D:
                case 0x7D: // JNL
                    op1 = (byte) next_byte();
                    if (sf == of) {
                        eip += op1;
                    }
                    return;
                case 0x6E:
                case 0x7E: // JLE
                    op1 = (byte) next_byte();
                    if (zf || sf != of) {
                        eip += op1;
                    }
                    return;
                case 0x6F:
                case 0x7F: // JNLE
                    op1 = (byte) next_byte();
                    if (!zf && sf == of) {
                        eip += op1;
                    }
                    return;
                case 0x80:
                case 0x82:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = (byte) next_byte();
                    res = this.run_arith(8, modrm >> 3 & 7, op1, op2);
                    if ((modrm >> 3 & 7) == 7) {
                        return;
                    }
                    write_rm8_IMM(modrm, res);
                    return;
                case 0x81:
                case 0x83:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    if ((opcode & 2) == 2) {
                        op2 = (byte) next_byte(); // 83
                    } else {
                        op2 = next_word(); // 81
                    }
                    res = this.run_arith(16, modrm >> 3 & 7, op1, op2);
                    if ((modrm >> 3 & 7) == 7) {
                        return;
                    }
                    write_rm16_IMM(modrm, res);
                    return;
                case 0x84: // TEST8
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 & op2;
                    set_bit_flags(8, op1, op2, res);
                    return;
                case 0x85:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 & op2;
                    set_bit_flags(16, op1, op2, res);
                    return;
                case 0x86: { // XCHG
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    write_reg8(modrm, op1);
                    write_rm8(modrm, op2);
                    return;
                }
                case 0x87: { // XCHG
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    write_reg16(modrm, op1);
                    write_rm16(modrm, op2);
                    return;
                }
                case 0x88: // MOV
                    modrm = next_byte();
                    write_rm8(modrm, read_reg8(modrm));
                    return;
                case 0x89: // MOV
                    modrm = next_byte();
                    write_rm16(modrm, read_reg16(modrm));
                    return;
                case 0x8A: // MOV
                    modrm = next_byte();
                    write_reg8(modrm, read_rm8(modrm));
                    return;
                case 0x8B: // MOV
                    modrm = next_byte();
                    write_reg16(modrm, read_rm16(modrm));
                    return;
                case 0x8C:
                    modrm = next_byte();
                    switch (modrm >> 3 & 7) {
                        case CS:
                            op1 = cs;
                            break;
                        case DS:
                            op1 = ds;
                            break;
                        case ES:
                            op1 = es;
                            break;
                        case SS:
                            op1 = ss;
                            break;
                        default:
                            throw new IllegalStateException("Unknown segment register\n");
                    }
                    write_rm16(modrm, op1);
                    return;
                case 0x8D: // LEA
                    modrm = next_byte();
                    if (modrm >> 6 == 3) {
                        throw new IllegalStateException("LEA with mod=3!");
                    }
                    write_reg16(modrm, decode_modrm(modrm));
                    return;
                case 0x8E:
                    modrm = next_byte();
                    op2 = read_rm16(modrm);
                    System.out.printf("%04x\n", op2);
                    switch (modrm >> 3 & 7) {
                        case CS:
                            cs = op2;
                            break;
                        case DS:
                            ds = op2;
                            break;
                        case ES:
                            es = op2;
                            break;
                        case SS:
                            ss = op2;
                            break;
                        default:
                            throw new IllegalStateException("Unknown segment register\n");
                    }
                    return;
                case 0x8F:
                    modrm = next_byte();
                    if ((modrm & 7) != 0) {
                        throw new IllegalStateException("PUSH.rm [8F] is not zero!");
                    }
                    write_rm16(modrm, pop16());
                    return;
                case 0x90: // NOP
                    return;
                case 0x91:
                case 0x92:
                case 0x93:
                case 0x94:
                case 0x95:
                case 0x96:
                case 0x97: // XCHG
                    op1 = opcode & 7;
                    registers[AX] ^= registers[op1];
                    registers[op1] ^= registers[AX];
                    registers[AX] ^= registers[op1];
                    return;
                case 0x98: // CBW
                    // https://www.felixcloutier.com/x86/CBW:CWDE:CDQE.html
                    registers[AX] = ((byte) registers[AX]) & 0xFFFF;
                    return;
                case 0x99: // CWD
                    // http://www.c-jump.com/CIS77/MLabs/M11arithmetic/M11_0110_cbw_cwd_cdq.htm
                    registers[DX] = (registers[AX] & 0x8000) == 0 ? 0xFFFF : 0;
                    return;
                case 0x9A: {
                    // CALLF
                    int temp = cs;
                    int temp1 = eip;
                    push16(temp1);

                    int temp3 = next_word();
                    int temp4 = next_word();
                    eip = temp3;
                    cs = temp4;
                    return;
                }
                case 0x9B: // WAIT
                    return;
                case 0x9C: // PUSHF
                    push16(get_eflags());
                    return;
                case 0x9D:
                    set_eflags(pop16());
                    return;
                case 0x9E: // SAHF
                    op1 = get_reg8(AH) & 0xD5; // Mask out flags we do not need
                    op2 = get_eflags() & ~0xFF; // Remove lower bits
                    set_eflags(op1 | op2);
                    return;
                case 0x9F: // LAHF
                    op1 = get_reg8(AH) & 0x2A; // Select the bits we will keep
                    op2 = get_eflags() & 0xD5; // Only the bits we need
                    set_reg8(AH, op1 | op2);
                    return;
                case 0xA0:
                    set_reg8(AL, read_byte(seg_translation_internal(ds, next_word())));
                    return;
                case 0xA1:
                    set_reg16(AX, read_word(seg_translation_internal(ds, next_word())));
                    return;
                case 0xA2:
                    write_byte(seg_translation_internal(ds, next_word()), get_reg8(AL));
                    return;
                case 0xA3:
                    write_word(seg_translation_internal(ds, next_word()), get_reg16(AX));
                    return;
                case 0xA4:
                    switch (rep) {
                        case NO_REP_PREFIX:
                            wb(es, registers[DI], rb(ds, registers[SI]));
                            if ((additional_eflags_bits & DF) == 0) {
                                registers[DI] = (registers[DI] + 1) & 0xFFFF;
                                registers[SI] = (registers[SI] + 1) & 0xFFFF;
                            } else {
                                registers[DI] = (registers[DI] - 1) & 0xFFFF;
                                registers[SI] = (registers[SI] - 1) & 0xFFFF;
                            }
                            break;
                        case REPNZ:
                        case REPZ:
                            int ofz = (additional_eflags_bits & DF) == 0 ? 1 : -1;
                            for (; registers[CX] != 0; registers[CX] = (registers[CX] - 1) & 0xFFFF) {
                                wb(es, registers[DI], rb(ds, registers[SI]));
                                registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                                registers[SI] = (registers[SI] + ofz) & 0xFFFF;
                            }
                            break;
                    }
                    return;
                case 0xA5:
                    switch (rep) {
                        case NO_REP_PREFIX:
                            ww(es, registers[DI], rw(ds, registers[SI]));
                            if ((additional_eflags_bits & DF) == 0) {
                                registers[DI] = (registers[DI] + 2) & 0xFFFF;
                                registers[SI] = (registers[SI] + 2) & 0xFFFF;
                            } else {
                                registers[DI] = (registers[DI] - 2) & 0xFFFF;
                                registers[SI] = (registers[SI] - 2) & 0xFFFF;
                            }
                            break;
                        case REPNZ:
                        case REPZ:
                            int ofz = (additional_eflags_bits & DF) == 0 ? 2 : -2;
                            for (; registers[CX] != 0; registers[CX] = (registers[CX] - 1) & 0xFFFF) {
                                ww(es, registers[DI], rw(ds, registers[SI]));
                                registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                                registers[SI] = (registers[SI] + ofz) & 0xFFFF;
                            }
                            break;
                    }
                    return;
                case 0xA6: {
                    int ofz = (additional_eflags_bits & DF) == 0 ? 1 : -1;
                    switch (rep) {
                        case 0:
                            op1 = rb(ds, registers[SI]);
                            op2 = rb(es, registers[DI]);
                            res = op1 - op2;
                            registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                            registers[SI] = (registers[SI] + ofz) & 0xFFFF;
                            set_sub_flags(8, op1, op2, res);
                            break;
                        case 1:
                        case 2:
                            boolean done = (rep & 2) == 2;
                            for (; registers[CX] != 0; registers[CX] = (registers[CX] - 1) & 0xFFFF) {
                                op1 = rb(ds, registers[SI]);
                                op2 = rb(es, registers[DI]);
                                res = op1 - op2;
                                registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                                registers[SI] = (registers[SI] + ofz) & 0xFFFF;
                                if ((res == 0) == done) {
                                    set_sub_flags(8, op1, op2, res);
                                    return;
                                }
                            }
                            return;
                    }
                    return;
                }
                case 0xA7: {
                    int ofz = (additional_eflags_bits & DF) == 0 ? 2 : -2;
                    switch (rep) {
                        case 0:
                            op1 = rw(ds, registers[SI]);
                            op2 = rw(es, registers[DI]);
                            res = op1 - op2;
                            registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                            registers[SI] = (registers[SI] + ofz) & 0xFFFF;
                            set_sub_flags(16, op1, op2, res);
                            break;
                        case 1:
                        case 2:
                            boolean done = (rep & 2) == 2;
                            for (; registers[CX] != 0; registers[CX] = (registers[CX] - 1) & 0xFFFF) {
                                op1 = rw(ds, registers[SI]);
                                op2 = rw(es, registers[DI]);
                                res = op1 - op2;
                                registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                                registers[SI] = (registers[SI] + ofz) & 0xFFFF;
                                if ((res == 0) == done) {
                                    set_sub_flags(16, op1, op2, res);
                                    return;
                                }
                            }
                            return;
                    }
                    return;
                }
                case 0xA8: // TEST
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = op1 & op2;
                    set_bit_flags(8, op1, op2, res);
                    return;
                case 0xA9: // TEST
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = op1 & op2;
                    set_bit_flags(16, op1, op2, res);
                    return;
                case 0xAA: {// STOSB
                    int ofz = (additional_eflags_bits & DF) == 0 ? 1 : -1;
                    switch (rep) {
                        case 0:
                            wb(es, registers[DI], get_reg8(AL));
                            registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                            break;
                        case 1:
                        case 2:
                            int al_value = get_reg8(AL);
                            for (; registers[CX] != 0; registers[CX] = (registers[CX] - 1) & 0xFFFF) {
                                wb(es, registers[DI], al_value);
                                registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                            }
                            break;
                    }
                    return;
                }
                case 0xAB: {// STOSW
                    int ofz = (additional_eflags_bits & DF) == 0 ? 2 : -2;
                    switch (rep) {
                        case 0:
                            ww(es, registers[DI], get_reg16(AX));
                            registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                            break;
                        case 1:
                        case 2:
                            int ax_value = get_reg16(AX);
                            for (; registers[CX] != 0; registers[CX] = (registers[CX] - 1) & 0xFFFF) {
                                ww(es, registers[DI], ax_value);
                                registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                            }
                            break;
                    }
                    return;
                }
                case 0xAC: // LODSB
                    set_reg8(AL, rb(ds, registers[SI]));
                    if ((additional_eflags_bits & DF) == 0) {
                        registers[SI] = (registers[SI] + 1) & 0xFFFF;
                    } else {
                        registers[SI] = (registers[SI] - 1) & 0xFFFF;
                    }
                    return;
                case 0xAD: // LODSW
                    set_reg16(AX, rw(ds, registers[SI]));
                    if ((additional_eflags_bits & DF) == 0) {
                        registers[SI] = (registers[SI] + 2) & 0xFFFF;
                    } else {
                        registers[SI] = (registers[SI] - 2) & 0xFFFF;
                    }
                    return;
                case 0xAE: {// SCASB
                    int ofz = (additional_eflags_bits & DF) == 0 ? 1 : -1;
                    int al_val = get_reg8(AL);
                    switch (rep) {
                        case 0:
                            op1 = rb(ds, registers[SI]);
                            op2 = al_val;
                            res = op1 - op2;
                            registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                            registers[SI] = (registers[SI] + ofz) & 0xFFFF;
                            set_sub_flags(8, op1, op2, res);
                            break;
                        case 1:
                        case 2:
                            boolean done = (rep & 2) == 2;
                            for (; registers[CX] != 0; registers[CX] = (registers[CX] - 1) & 0xFFFF) {
                                op1 = rb(ds, registers[SI]);
                                op2 = al_val;
                                res = op1 - op2;
                                registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                                registers[SI] = (registers[SI] + ofz) & 0xFFFF;
                                if ((res == 0) == done) {
                                    set_sub_flags(8, op1, op2, res);
                                    return;
                                }
                            }
                            return;
                    }
                    return;
                }
                case 0xAF: {// SCASW
                    int ofz = (additional_eflags_bits & DF) == 0 ? 2 : -2;
                    int ax_val = get_reg16(AX);
                    switch (rep) {
                        case 0:
                            op1 = rw(ds, registers[SI]);
                            op2 = ax_val;
                            res = op1 - op2;
                            registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                            registers[SI] = (registers[SI] + ofz) & 0xFFFF;
                            set_sub_flags(16, op1, op2, res);
                            break;
                        case 1:
                        case 2:
                            boolean done = (rep & 2) == 2;
                            for (; registers[CX] != 0; registers[CX] = (registers[CX] - 1) & 0xFFFF) {
                                op1 = rw(ds, registers[SI]);
                                op2 = ax_val;
                                res = op1 - op2;
                                registers[DI] = (registers[DI] + ofz) & 0xFFFF;
                                registers[SI] = (registers[SI] + ofz) & 0xFFFF;
                                if ((res == 0) == done) {
                                    set_sub_flags(16, op1, op2, res);
                                    return;
                                }
                            }
                            return;
                    }
                    return;
                }
                case 0xB0:
                case 0xB1:
                case 0xB2:
                case 0xB3:
                case 0xB4:
                case 0xB5:
                case 0xB6:
                case 0xB7: // MOV
                    set_reg8(opcode & 7, next_byte());
                    return;
                case 0xB8:
                case 0xB9:
                case 0xBA:
                case 0xBB:
                case 0xBC:
                case 0xBD:
                case 0xBE:
                case 0xBF: // MOV
                    set_reg16(opcode & 7, next_word());
                    return;
                case 0xC0:
                case 0xC2: // RET imm
                    op1 = next_word();
                    eip = pop16();
                    registers[SP] = (registers[SP] + op1) & 0xFFFF;
                    return;
                case 0xC1:
                case 0xC3: // RET normal
                    eip = pop16();
                    return;
                case 0xC4: // LES
                    modrm = next_byte();
                    if (modrm >> 6 == 3) {
                        throw new IllegalStateException("LES w/ mod=3");
                    }
                    op1 = decode_modrm(modrm);
                    write_reg16(modrm, read_word(op1));
                    es = read_word(op1 + 2);
                    return;
                case 0xC5: // LDS
                    modrm = next_byte();
                    if (modrm >> 6 == 3) {
                        throw new IllegalStateException("LES w/ mod=3");
                    }
                    op1 = decode_modrm(modrm);
                    write_reg16(modrm, read_word(op1));
                    ds = read_word(op1 + 2);
                    return;
                case 0xC6: // MOV
                    modrm = next_byte();
                    if (modrm >> 6 == 3) {
                        throw new IllegalStateException("C6/C7 w/ mod=3");
                    }
                    read_rm8(modrm);
                    write_rm8_IMM(modrm, next_byte()); // TODO: Massive bug here
                    return;
                case 0xC7: // MOV
                    modrm = next_byte();
                    if (modrm >> 6 == 3) {
                        throw new IllegalStateException("C6/C7 w/ mod=3");
                    }
                    read_rm8(modrm);
                    write_rm16_IMM(modrm, next_word());
                    return;
                case 0xC8: {
                    int tmp = next_word();
                    int tmp1 = next_byte();
                    int tmp2 = tmp1 & 0x1F;
                    push16(registers[BP]);
                    int tmp3 = registers[SP];
                    if (tmp2 > 0) {
                        for (int i = 1; i < tmp2; i++) {
                            registers[BP] = (registers[BP] - 2) & 0xFFFF;
                            push16(rw(ss, registers[BP]));
                        }
                        push16(tmp3);
                    }
                    registers[BP] = tmp3;
                    registers[SP] = (registers[SP] - tmp) & 0xFFFF;
                    return;
                }
                case 0xC9: // LEAVE
                    registers[SP] = registers[BP];
                    registers[BP] = pop16();
                    return;
                case 0xCA: { // RETF
                    int tmp = next_word();
                    eip = pop16();
                    cs = pop16();
                    registers[SP] = (registers[SP] + tmp + 2) & 0xFFFF;
                    return;
                }
                case 0xCB: { // RETF
                    eip = pop16();
                    cs = pop16();
                    return;
                }
                case 0xCC: { // INT3
                    interrupt(3);
                    return;
                }
                case 0xCD: { // INT
                    op1 = next_byte();
                    interrupt(op1);
                    return;
                }
                case 0xCE: { // INTO
                    op1 = next_byte();
                    if (of) {
                        interrupt(op1);
                    }
                    return;
                }
                case 0xCF: { // IRET
                    eip = pop16();
                    cs = pop16();
                    set_eflags(pop16());
                    return;
                }
                case 0xD0:
                case 0xD2: {
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = (opcode & 2) == 2 ? get_reg8(CL) : 1;
                    if (op2 == 0) {
                        return; // Nothing is modified if cnt == 0
                    }
                    res = 0;
                    switch (modrm >> 3 & 7) {
                        case 0: // ROL
                            if (architecture == 80186) {
                                op2 &= 7;
                            }
                            res = op1 << op2 | op1 >>> (8 - op2);
                            cf = (op1 >> 7 & 1) != 0;
                            of = (((op1 >> 7) ^ (op1 >> 6)) & 1) != 0;
                            break;
                        case 1: // ROR
                            if (architecture == 80186) {
                                op2 &= 7;
                            }
                            res = op1 >>> op2 | op1 << (8 - op2);
                            cf = (op1 >> 7 & 1) != 0;
                            of = (((res >> 7) ^ (res >> 6)) & 1) != 0;
                            break;
                        case 2: // RCL
                            if (architecture == 80186) {
                                op1 %= 9;
                            }
                            res = (op1 << op2) | (cf ? 1 : 0) << (op2 - 1) | op1 >>> (9 - op2);
                            cf = (op1 >> 8 & 1) != 0;
                            of = ((op1 >> 7) ^ (cf ? 1 : 0)) != 0;
                            break;
                        case 3: // RCR
                            if (architecture == 80186) {
                                op1 %= 9;
                            }
                            res = (op1 >>> op2) | (cf ? 1 : 0) << (8 - op2) | op1 << (9 - op2);
                            cf = (op1 >> 8 & 1) != 0;
                            of = (((res >> 7) ^ (res >> 6)) & 1) != 0;
                            break;
                        case 4: // SHL
                        case 6: // SAL
                            if (architecture == 80186) {
                                op1 &= 7;
                            }
                            res = op1 << op2;
                            cf = (op1 >> (8 - op2) & 1) != 0;
                            of = ((res >>> 7) ^ (cf ? 1 : 0)) != 0;
                            this.set_zf_pf_sf(res, 8);
                            af = false;
                            break;
                        case 5: // SHR
                            if (architecture == 80186) {
                                op1 &= 7;
                            }
                            res = op1 >>> op2;
                            cf = ((op1 & (1 << (op2 - 1))) >> (op2 - 1)) != 0;
                            of = (res << 1 ^ res) >> 7 != 0;
                            this.set_zf_pf_sf(res, 8);
                            af = false;
                            break;
                        case 7: // SAR
                            if (architecture == 80186) {
                                op1 &= 7;
                            }
                            res = (byte) op1 >> op2;
                            cf = (op1 >> op2 - 1 & 0x1) != 0;
                            of = false;
                            this.set_zf_pf_sf(res, 8);
                            af = false;
                            break;
                        default:
                            throw new Error("Invalid DX opcode!");
                    }
                    write_rm8(modrm, res);
                    return;
                }
                case 0xD1:
                case 0xD3: {
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = (opcode & 2) == 2 ? get_reg8(CL) : 1;
                    if (op2 == 0) {
                        return; // Nothing is modified if cnt == 0
                    }
                    res = 0;
                    switch (modrm >> 3 & 7) {
                        case 0: // ROL
                            if (architecture == 80186) {
                                op2 &= 15;
                            }
                            res = op1 << op2 | op1 >>> (16 - op2);
                            cf = (op1 >> 15 & 1) != 0;
                            of = (((op1 >> 15) ^ (op1 >> 14)) & 1) != 0;
                            break;
                        case 1: // ROR
                            if (architecture == 80186) {
                                op2 &= 15;
                            }
                            res = op1 >>> op2 | op1 << (16 - op2);
                            cf = (op1 >> 15 & 1) != 0;
                            of = (((res >> 15) ^ (res >> 14)) & 1) != 0;
                            break;
                        case 2: // RCL
                            if (architecture == 80186) {
                                op1 %= 17;
                            }
                            res = (op1 << op2) | (cf ? 1 : 0) << (op2 - 1) | op1 >>> (16 - op2);
                            cf = (op1 >> 16 & 1) != 0;
                            of = ((op1 >> 15) ^ (cf ? 1 : 0)) != 0;
                            break;
                        case 3: // RCR
                            if (architecture == 80186) {
                                op1 %= 17;
                            }
                            res = (op1 >>> op2) | (cf ? 1 : 0) << (16 - op2) | op1 << (17 - op2);
                            cf = (op1 >> 16 & 1) != 0;
                            of = (((res >> 15) ^ (res >> 14)) & 1) != 0;
                            break;
                        case 4: // SHL
                        case 6: // SAL
                            if (architecture == 80186) {
                                op1 &= 15;
                            }
                            res = op1 << op2;
                            cf = (op1 >> (16 - op2) & 1) != 0;
                            of = ((res >>> 15) ^ (cf ? 1 : 0)) != 0;
                            this.set_zf_pf_sf(res, 16);
                            af = false;
                            break;
                        case 5: // SHR
                            if (architecture == 80186) {
                                op1 &= 15;
                            }
                            res = op1 >>> op2;
                            cf = ((op1 & (1 << (op2 - 1))) >> (op2 - 1)) != 0;
                            of = (res << 1 ^ res) >> 15 != 0;
                            this.set_zf_pf_sf(res, 16);
                            af = false;
                            break;
                        case 7: // SAR
                            if (architecture == 80186) {
                                op1 &= 7;
                            }
                            res = (short) op1 >> op2;
                            cf = (op1 >> op2 - 1 & 0x1) != 0;
                            of = false;
                            this.set_zf_pf_sf(res, 16);
                            af = false;
                            break;
                        default:
                            throw new Error("Invalid DX opcode!");
                    }
                    write_rm16(modrm, res);
                    return;
                }
                case 0xD4: {
                    // https://www.felixcloutier.com/x86/AAM.html
                    int tempAL = get_reg8(AL);
                    int i8 = next_byte();
                    set_reg8(AH, tempAL / i8);
                    set_reg8(AL, tempAL % i8);
                    of = af = cf = false;
                    this.set_zf_pf_sf(get_reg8(AL), 8);
                    return;
                }
                case 0xD5: {
                    // https://www.felixcloutier.com/x86/AAD.html
                    int tempAL = get_reg8(AL);
                    int tempAH = get_reg8(AH);
                    int i8 = next_byte();
                    set_reg8(AH, 0);
                    set_reg8(AL, (tempAL + (tempAH * i8)) & 0xFF);
                    //this.set_zf_pf_sf(get_reg8(AL), 8);
                    this.set_add_flags(8, tempAL, (tempAH * i8), get_reg8(AL));
                    return;
                }
                case 0xD6: { // SALC
                    // http://www.rcollins.org/secrets/opcodes/SALC.html
                    set_reg8(AL, cf ? 0xFF : 0);
                    return;
                }
                case 0xD7: {// XLAT
                    set_reg8(AL, rb(ds, registers[BX] + get_reg8(AL)));
                    return;
                }
                case 0xD8:
                case 0xD9:
                case 0xDA:
                case 0xDB:
                case 0xDC:
                case 0xDE:
                case 0xDF:
                    if (fpu_enabled == 1) {
                        fpu.op(opcode, next_byte());
                    } else {
                        System.err.println("Ignoring FPU operation!");
                        next_byte(); // ModR/M
                    }
                    return;
                case 0xE0:
                    // LOOPNZ
                    op1 = next_byte();
                    registers[CX] = (registers[CX] - 1) & 0xFFFF;
                    if (registers[CX] != 0 && !zf) {
                        eip = (eip + (byte) op1) & 0xFFFF;
                    }
                    return;
                case 0xE1:
                    // LOOPZ
                    op1 = next_byte();
                    registers[CX] = (registers[CX] - 1) & 0xFFFF;
                    if (registers[CX] != 0 && zf) {
                        eip = (eip + (byte) op1) & 0xFFFF;
                    }
                    return;
                case 0xE2:
                    // LOOPZ
                    op1 = next_byte();
                    registers[CX] = (registers[CX] - 1) & 0xFFFF;
                    if (registers[CX] != 0) {
                        eip = (eip + (byte) op1) & 0xFFFF;
                    }
                    return;
                case 0xE3:
                    // LOOPZ
                    op1 = next_byte();
                    if (registers[CX] == 0) {
                        eip = (eip + (byte) op1) & 0xFFFF;
                    }
                    return;
                // I/O INSTRUCTIONS
                case 0xE4:
                    op1 = next_byte();
                    set_reg8(AL, IO.read_port(op1));
                    return;
                case 0xE5:
                    op1 = next_byte();
                    registers[AX] = IO.read_port(op1);
                    return;
                case 0xE6:
                    IO.write_port(next_byte(), get_reg8(AL));
                    return;
                case 0xE7:
                    IO.write_port(next_byte(), registers[AX]);
                    return;
                case 0xE8:
                    // CALL
                    push16(eip);
                    op1 = next_word();
                    eip = (eip + op1) & 0xFFFF;
                    return;
                case 0xE9:
                    // JMP
                    op1 = next_word();
                    eip = (eip + op1) & 0xFFFF;
                    return;
                case 0xEA: {
                    // JMPF
                    int tmp1 = next_word();
                    int tmp2 = next_word();
                    eip = tmp1;
                    cs = tmp2;
                    return;
                }
                case 0xEB:
                    // JMP
                    op1 = next_byte();
                    eip = (eip + (byte) op1) & 0xFFFF;
                    return;
                case 0xEC:
                    // IN
                    set_reg8(AL, IO.read_port(registers[DX]));
                    return;
                case 0xED:
                    // IN
                    set_reg16(AX, IO.read_port(registers[DX]));
                    return;
                case 0xEE:
                    // OUT
                    IO.write_port(registers[DX], get_reg8(AL));
                    return;
                case 0xEF:
                    // OUT
                    IO.write_port(registers[DX], registers[AX]);
                    return;
                case 0xF0:
                case 0xF1: // ?
                    // LOCK
                    System.out.println("CPU: LOCK prefix");
                    continue;
                case 0xF2:
                    rep = REPNZ;
                    continue;
                case 0xF3:
                    rep = REPZ;
                    continue;
                case 0xF4:
                    throw new HLTException();
                case 0xF5: // CMC
                    cf = !cf;
                    return;
                case 0xF6:
                    modrm = next_byte();
                    switch (modrm >> 3 & 7) {
                        case 0:
                        case 1: // TEST
                            op1 = read_rm8(modrm);
                            op2 = next_byte();
                            res = op1 & op2;
                            set_bit_flags(8, op1, op2, res);
                            return;
                        case 2: // NOT
                            write_rm8(modrm, ~read_rm8(modrm));
                            return;
                        case 3:
                            op1 = 0;
                            op2 = read_rm8(modrm);
                            res = op1 - op2;
                            set_sub_flags(8, op1, op2, res);
                            return;
                        case 4: // MUL
                            op1 = read_rm8(modrm);
                            op2 = get_reg8(AL);
                            res = op1 * op2;
                            if ((op1 >> 8) != 0) {
                                of = cf = true;
                            } else {
                                of = cf = false;
                            }
                            registers[AX] = res;
                            return;
                        case 5:  // IMUL
                            op1 = (byte) read_rm8(modrm);
                            op2 = (byte) get_reg8(AL);
                            res = (short) (op1 * op2);
                            if ((op1 >> 8) != 0) {
                                of = cf = true;
                            } else {
                                of = cf = false;
                            }
                            registers[AX] = res & 0xFFFF;
                            return;
                        case 6:
                            op1 = read_rm8(modrm);
                            op2 = get_reg8(AL);
                            res = (op2 / op1);
                            int res2 = (op2 % op1);
                            set_reg8(AL, res);
                            set_reg8(AH, res2);
                            return;
                        case 7:
                            op1 = (byte) read_rm8(modrm);
                            op2 = (byte) get_reg8(AL);
                            res = (short) (op2 / op1);
                            int res3 = (short) (op2 % op1);
                            set_reg8(AL, res);
                            set_reg8(AH, res3);
                            return;
                    }
                    return;
                case 0xF7:
                    modrm = next_byte();
                    switch (modrm >> 3 & 7) {
                        case 0:
                        case 1: // TEST
                            op1 = read_rm16(modrm);
                            op2 = next_word();
                            res = op1 & op2;
                            set_bit_flags(16, op1, op2, res);
                            return;
                        case 2: // NOT
                            write_rm16(modrm, ~read_rm16(modrm));
                            return;
                        case 3:
                            op1 = 0;
                            op2 = read_rm16(modrm);
                            res = op1 - op2;
                            set_sub_flags(16, op1, op2, res);
                            return;
                        case 4: // MUL
                            op1 = read_rm16(modrm);
                            op2 = get_reg16(AX);
                            res = op1 * op2;
                            if ((op1 >> 16) != 0) {
                                of = cf = true;
                            } else {
                                of = cf = false;
                            }
                            registers[AX] = res & 0xFFFF;
                            registers[DX] = res >> 16;
                            return;
                        case 5:  // IMUL
                            op1 = (short) read_rm16(modrm);
                            op2 = (short) get_reg16(AL);
                            res = (op1 * op2);
                            if ((op1 >> 16) != 0) {
                                of = cf = true;
                            } else {
                                of = cf = false;
                            }
                            registers[AX] = res & 0xFFFF;
                            registers[DX] = res >> 16;
                            return;
                        case 6:
                            op1 = read_rm16(modrm);
                            op2 = get_reg16(AL);
                            res = (op2 / op1);
                            int res2 = (op2 % op1);
                            set_reg16(AX, res);
                            set_reg16(DX, res2);
                            return;
                        case 7:
                            op1 = (short) read_rm8(modrm);
                            op2 = (short) get_reg8(AL);
                            res = (op2 / op1);
                            int res3 = (op2 % op1);
                            set_reg16(AX, res);
                            set_reg16(DX, res3);
                            return;
                    }
                    return;
                case 0xF8: // CLC
                    cf = false;
                    return;
                case 0xF9: // STC
                    cf = true;
                    return;
                case 0xFA: // CLI
                    this.additional_eflags_bits &= 0xFDFF;
                    return;
                case 0xFB: // STI
                    this.additional_eflags_bits |= 0x200;
                    return;
                case 0xFC: // CLD
                    this.additional_eflags_bits &= 0xFBFF;
                    return;
                case 0xFD: // STD
                    this.additional_eflags_bits |= 0x400;
                    return;
                case 0xFE:
                    modrm = next_byte();
                    switch (modrm >> 3 & 7) {
                        case 0:
                        case 2:
                        case 4:
                        case 6: {
                            boolean saved_cf = cf;
                            op1 = read_rm8(modrm);
                            op2 = 1;
                            res = (op1 + op2);
                            set_add_flags(8, op1, op2, res);
                            cf = saved_cf;
                            return;
                        }
                        case 1:
                        case 3:
                        case 5:
                        case 7: {
                            boolean saved_cf = cf;
                            op1 = read_rm8(modrm);
                            op2 = 1;
                            res = (op1 - op2);
                            set_sub_flags(8, op1, op2, res);
                            cf = saved_cf;
                            return;
                        }
                    }
                    return;
                case 0xFF:
                    modrm = next_byte();
                    switch (modrm >> 3 & 7) {
                        case 0: {
                            boolean saved_cf = cf;
                            op1 = read_rm16(modrm);
                            op2 = 1;
                            res = (op1 + op2);
                            set_add_flags(16, op1, op2, res);
                            cf = saved_cf;
                            return;
                        }
                        case 2:
                            op1 = read_rm16(modrm);
                            push16(eip);
                            eip = op1;
                            return;
                        case 4:
                            op1 = read_rm16(modrm);
                            eip = op1;
                            return;
                        case 5: // JMPF
                        {
                            if (modrm >> 6 == 3) {
                                throw new IllegalStateException("JMPF FF with MOD=3??");
                            }
                            int rm = this.decode_modrm(modrm);
                            eip = rw($cseg, rm + 0);
                            cs = rw($cseg, rm + 2);
                            return;
                        }
                        case 3: {// CALLF
                            push16(cs);
                            push16(eip);
                            if (modrm >> 6 == 3) {
                                throw new IllegalStateException("JMPF FF with MOD=3??");
                            }
                            int rm = this.decode_modrm(modrm);
                            eip = rw($cseg, rm + 0);
                            cs = rw($cseg, rm + 2);
                            return;
                        }
                        case 6: // PUSH
                            if (modrm == 0xF4) {
                                if (architecture == 8086) {
                                    registers[SP] = (registers[SP] - 2) & 0xFFFF;
                                    ww(ss, registers[SP], registers[SP]); // Pushes post sp-modified ESP
                                } else {
                                    push16(registers[SP]);
                                }
                            } else {
                                push16(read_rm16(modrm));
                            }
                            return;
                        case 1: {
                            boolean saved_cf = cf;
                            op1 = read_rm16(modrm);
                            op2 = 1;
                            res = (op1 - op2);
                            set_sub_flags(16, op1, op2, res);
                            cf = saved_cf;
                            return;
                        }
                        case 7:
                            throw new IllegalStateException("UNKNOWN FF OP!");
                    }
                    return;
                default:
                    throw new UnsupportedOperationException(String.format("Opcode %02x not found!", opcode));
            }
        }
    }
}
