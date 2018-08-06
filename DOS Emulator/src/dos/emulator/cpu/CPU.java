/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dos.emulator.cpu;

import dos.emulator.IO;

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
     * Holds memory mappings
     */
    MemoryMap[] memory_maps;

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

        memory_maps = new MemoryMap[256];
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
        write_byte(seg_translation(seg, offset), value);
    }

    // Reading
    private int rb(int seg, int offset) {
        return read_byte(seg_translation(seg, offset));
    }

    private int rw(int seg, int offset) {
        return read_word(seg_translation(seg, offset));
    }

    private int seg_translation_internal(int sreg, int addr) {
        if (current_sreg != -1) {
            sreg = current_sreg;
            current_sreg = -1;
        }
        return seg_translation(sreg, addr);
    }

    private int decode_modrm(int modrm) {
        switch (modrm & ~0x38) {
            case 0:
                return seg_translation_internal(ds, registers[BX] + registers[SI]);
            case 1:
                return seg_translation_internal(ds, registers[BX] + registers[DI]);
            case 2:
                return seg_translation_internal(ss, registers[BP] + registers[SI]);
            case 3:
                return seg_translation_internal(ss, registers[BP] + registers[DI]);
            case 4:
                return seg_translation_internal(ds, registers[SI]);
            case 5:
                return seg_translation_internal(ds, registers[DI]);
            case 6:
                return seg_translation_internal(ds, next_word());
            case 7:
                return seg_translation_internal(ds, registers[BX]);
            case 0x40:
                return seg_translation_internal(ds, registers[BX] + registers[SI] + next_byte());
            case 0x41:
                return seg_translation_internal(ds, registers[BX] + registers[DI] + next_byte());
            case 0x42:
                return seg_translation_internal(ss, registers[BP] + registers[SI] + next_byte());
            case 0x43:
                return seg_translation_internal(ss, registers[BP] + registers[DI] + next_byte());
            case 0x44:
                return seg_translation_internal(ds, registers[SI] + next_byte());
            case 0x45:
                return seg_translation_internal(ds, registers[DI] + next_byte());
            case 0x46:
                return seg_translation_internal(ss, registers[BP] + next_byte());
            case 0x47:
                return seg_translation_internal(ds, registers[BX] + next_byte());
            case 0x80:
                return seg_translation_internal(ds, registers[BX] + registers[SI] + next_word());
            case 0x81:
                return seg_translation_internal(ds, registers[BX] + registers[DI] + next_word());
            case 0x82:
                return seg_translation_internal(ss, registers[BP] + registers[SI] + next_word());
            case 0x83:
                return seg_translation_internal(ss, registers[BP] + registers[DI] + next_word());
            case 0x84:
                return seg_translation_internal(ds, registers[SI] + next_word());
            case 0x85:
                return seg_translation_internal(ds, registers[DI] + next_word());
            case 0x86:
                return seg_translation_internal(ss, registers[BP] + next_word());
            case 0x87:
                return seg_translation_internal(ds, registers[BX] + next_word());
            default:
                throw new IllegalStateException("Unknown ModR/M value: " + Integer.toHexString(modrm & ~0x38));
        }
    }

    private int discard_first_op(int $, int value) {
        return value & 0xFFFF;
    }

    /**
     * Helps with LEA
     *
     * @param modrm
     * @return
     */
    private int lea_op(int modrm) {
        switch (modrm & ~0x38) {
            case 0:
                return discard_first_op(ds, registers[BX] + registers[SI]);
            case 1:
                return discard_first_op(ds, registers[BX] + registers[DI]);
            case 2:
                return discard_first_op(ss, registers[BP] + registers[SI]);
            case 3:
                return discard_first_op(ss, registers[BP] + registers[DI]);
            case 4:
                return discard_first_op(ds, registers[SI]);
            case 5:
                return discard_first_op(ds, registers[DI]);
            case 6:
                return discard_first_op(ds, next_word());
            case 7:
                return discard_first_op(ds, registers[BX]);
            case 0x40:
                return discard_first_op(ds, registers[BX] + registers[SI] + next_byte());
            case 0x41:
                return discard_first_op(ds, registers[BX] + registers[DI] + next_byte());
            case 0x42:
                return discard_first_op(ss, registers[BP] + registers[SI] + next_byte());
            case 0x43:
                return discard_first_op(ss, registers[BP] + registers[DI] + next_byte());
            case 0x44:
                return discard_first_op(ds, registers[SI] + next_byte());
            case 0x45:
                return discard_first_op(ds, registers[DI] + next_byte());
            case 0x46:
                return discard_first_op(ss, registers[BP] + next_byte());
            case 0x47:
                return discard_first_op(ds, registers[BX] + next_byte());
            case 0x80:
                return discard_first_op(ds, registers[BX] + registers[SI] + next_word());
            case 0x81:
                return discard_first_op(ds, registers[BX] + registers[DI] + next_word());
            case 0x82:
                return discard_first_op(ss, registers[BP] + registers[SI] + next_word());
            case 0x83:
                return discard_first_op(ss, registers[BP] + registers[DI] + next_word());
            case 0x84:
                return discard_first_op(ds, registers[SI] + next_word());
            case 0x85:
                return discard_first_op(ds, registers[DI] + next_word());
            case 0x86:
                return discard_first_op(ss, registers[BP] + next_word());
            case 0x87:
                return discard_first_op(ds, registers[BX] + next_word());
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
            return read_byte(decode_modrm(modrm));
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
            write_byte(decode_modrm(modrm), value);
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

    /**
     * Read and operand pointed to by an r/m8.
     *
     * @param modrm
     * @return
     */
    public int read_rm16(int modrm) {
        if (modrm < 0xC0) {
            return read_word(decode_modrm(modrm));
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
            write_word(decode_modrm(modrm), value);
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
        registers[id & 3] = (value & 0xFF) << shift_constant;
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
        pf = parity_table[res & 0xFF] == 1;
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
        cf = (left & (1 << size)) != 0;
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

    // Main loop
    public void run() {
        while (true) {
            run_instruction();
            current_sreg = -1;
            rep = 0;
        }
    }
    
    /**
     * Call interrupt vector!
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

    private void run_instruction() {
        while (true) {
            int opcode = next_byte();
            int modrm = 0, op1, op2, res;
            switch (opcode) {
                // ADD
                case 0x00:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 + op2;
                    set_add_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    return;
                case 0x01:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 + op2;
                    set_add_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    return;
                case 0x02:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = op1 + op2;
                    set_add_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    return;
                case 0x03:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = op1 + op2;
                    set_add_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    return;
                case 0x04:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = op1 + op2;
                    set_add_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    return;
                case 0x05:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = op1 + op2;
                    set_add_flags(16, op1, op2, res);
                    set_reg16(AX, res);
                    return;
                case 0x06:
                    push16(es);
                    return;
                case 0x07:
                    es = pop16();
                    return;

                // OR
                case 0x08:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 | op2;
                    set_bit_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    return;
                case 0x09:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 | op2;
                    set_bit_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    return;
                case 0x0A:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = op1 | op2;
                    set_bit_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    return;
                case 0x0B:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = op1 | op2;
                    set_bit_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    return;
                case 0x0C:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = op1 | op2;
                    set_bit_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    return;
                case 0x0D:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = op1 | op2;
                    set_bit_flags(16, op1, op2, res);
                    set_reg16(AX, res);
                    return;
                case 0x0E:
                    push16(cs);
                    return;
                case 0x0F:
                    cs = pop16();
                    return;

                // ADC
                case 0x10:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = (cf ? 1 : 0) + (op1 + op2);
                    set_add_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    return;
                case 0x11:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = (cf ? 1 : 0) + (op1 + op2);
                    set_add_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    return;
                case 0x12:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = (cf ? 1 : 0) + (op1 + op2);
                    set_add_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    return;
                case 0x13:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = (cf ? 1 : 0) + (op1 + op2);
                    set_add_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    return;
                case 0x14:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = (cf ? 1 : 0) + op1 + op2;
                    set_add_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    return;
                case 0x15:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = (cf ? 1 : 0) + (op1 + op2);
                    set_add_flags(16, op1, op2, res);
                    set_reg16(AX, res);
                    return;
                case 0x16:
                    push16(ss);
                    return;
                case 0x17:
                    ss = pop16();
                    return;

                // SBB
                case 0x18:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    return;
                case 0x19:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    return;
                case 0x1A:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    return;
                case 0x1B:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    return;
                case 0x1C:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    return;
                case 0x1D:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(16, op1, op2, res);
                    set_reg16(AX, res);
                    return;
                case 0x1E:
                    push16(ds);
                    return;
                case 0x1F:
                    ds = pop16();
                    return;

                // AND
                case 0x20:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 & op2;
                    set_bit_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    return;
                case 0x21:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 & op2;
                    set_bit_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    return;
                case 0x22:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = (op1 & op2);
                    set_bit_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    return;
                case 0x23:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = (op1 & op2);
                    set_bit_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    return;
                case 0x24:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = (op1 & op2);
                    set_bit_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    return;
                case 0x25:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = op1 & op2;
                    set_bit_flags(16, op1, op2, res);
                    set_reg16(AX, res);
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
                    return;
                }

                // SUB
                case 0x28:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 - op2;
                    set_sub_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    return;
                case 0x29:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 - op2;
                    set_sub_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    return;
                case 0x2A:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = (op1 - op2);
                    set_sub_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    return;
                case 0x2B:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = (op1 - op2);
                    set_sub_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    return;
                case 0x2C:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = (op1 - op2);
                    set_sub_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    break;
                case 0x2D:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = op1 - op2;
                    set_sub_flags(16, op1, op2, res);
                    set_reg16(AX, res);
                    return;
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
                    set_reg8(AL, al);
                    return;
                }

                // XOR
                case 0x30:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 ^ op2;
                    set_bit_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    return;
                case 0x31:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 ^ op2;
                    set_bit_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    return;
                case 0x32:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = op1 ^ op2;
                    set_bit_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    return;
                case 0x33:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = op1 ^ op2;
                    set_bit_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    return;
                case 0x34:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = (op1 ^ op2);
                    set_bit_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    break;
                case 0x35:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = op1 ^ op2;
                    set_bit_flags(16, op1, op2, res);
                    set_reg16(AX, res);
                    return;
                case 0x36:
                    current_sreg = ss;
                    continue;
                case 0x37:
                    // https://www.felixcloutier.com/x86/AAA.html
                    if (((get_reg8(AL) & 15) > 9) || af) {
                        registers[AX] = (registers[AX] + 0x106) & 0xFFFF;
                        af = true;
                        cf = true;
                    } else {
                        af = false;
                        cf = false;
                    }
                    set_reg8(AL, 0x0F & get_reg8(AL));
                    return;

                // CMP
                case 0x38:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 - op2;
                    set_sub_flags(8, op1, op2, res);
                    return;
                case 0x39:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 - op2;
                    set_sub_flags(16, op1, op2, res);
                    return;
                case 0x3A:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = op1 - op2;
                    set_sub_flags(8, op1, op2, res);
                    return;
                case 0x3B:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = op1 - op2;
                    set_sub_flags(16, op1, op2, res);
                    return;
                case 0x3C:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = (op1 - op2);
                    set_sub_flags(8, op1, op2, res);
                    return;
                case 0x3D:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = op1 - op2;
                    set_sub_flags(16, op1, op2, res);
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
                case 0x54:
                case 0x55:
                case 0x56:
                case 0x57:
                    push16(registers[opcode & 7]);
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
                    switch (modrm >> 3 & 7) {
                        case 0:
                            res = op1 + op2;
                            set_add_flags(8, op1, op2, res);
                            break;
                        case 1:
                            res = op1 | op2;
                            set_bit_flags(8, op1, op2, res);
                            break;
                        case 2:
                            res = cf ? 1 : 0 + op1 + op2;
                            set_add_flags(8, op1, op2, res);
                            break;
                        case 3:
                            res = op1 - op2;
                            res -= cf ? 1 : 0;
                            set_sub_flags(8, op1, op2, res);
                            break;
                        case 4:
                            res = op1 & op2;
                            set_bit_flags(8, op1, op2, res);
                            break;
                        case 5:
                            res = op1 - op2;
                            set_sub_flags(8, op1, op2, res);
                            break;
                        case 6:
                            res = op1 ^ op2;
                            set_bit_flags(8, op1, op2, res);
                            break;
                        case 7:
                            res = op1 - op2;
                            set_sub_flags(8, op1, op2, res);
                            return;
                        default:
                            throw new Error("Unknown GRP3/16 opcode!");
                    }
                    write_rm8(modrm, res);
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
                    switch (modrm >> 3 & 7) {
                        case 0:
                            res = op1 + op2;
                            set_add_flags(16, op1, op2, res);
                            break;
                        case 1:
                            res = op1 | op2;
                            set_bit_flags(16, op1, op2, res);
                            break;
                        case 2:
                            res = cf ? 1 : 0 + op1 + op2;
                            set_add_flags(16, op1, op2, res);
                            break;
                        case 3:
                            res = op1 - op2;
                            res -= cf ? 1 : 0;
                            set_sub_flags(16, op1, op2, res);
                            break;
                        case 4:
                            res = op1 & op2;
                            set_bit_flags(16, op1, op2, res);
                            break;
                        case 5:
                            res = op1 - op2;
                            set_sub_flags(16, op1, op2, res);
                            break;
                        case 6:
                            res = op1 ^ op2;
                            set_bit_flags(16, op1, op2, res);
                            break;
                        case 7:
                            res = op1 - op2;
                            set_sub_flags(16, op1, op2, res);
                            return;
                        default:
                            throw new Error("Unknown GRP3/16 opcode!");
                    }
                    write_rm16(modrm, res);
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
                    write_reg16(modrm, lea_op(modrm));
                    return;
                case 0x8E:
                    modrm = next_byte();
                    op2 = read_rm16(modrm);
                    switch (op2) {
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
                    eip = next_word();
                    cs = next_word();
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
                    write_rm8(modrm, next_byte());
                    return;
                case 0xC7: // MOV
                    modrm = next_byte();
                    if (modrm >> 6 == 3) {
                        throw new IllegalStateException("C6/C7 w/ mod=3");
                    }
                    write_rm16(modrm, next_word());
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
                    if(of){
                        interrupt(op1);
                    }
                    return;
                }
                case 0xCF: { // IRET
                    eip = pop16();
                    cs = pop16();
                    set_eflags(pop16());
                }
                case 0xE6:
                    IO.write_port(next_byte(), get_reg8(AL));
                    return;
                default:
                    throw new UnsupportedOperationException(String.format("Opcode %02x not found!", opcode));
            }
        }
    }
}

