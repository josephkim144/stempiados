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

    // Main loop
    public void run() {
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
                    continue;
                case 0x01:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 + op2;
                    set_add_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    continue;
                case 0x02:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = op1 + op2;
                    set_add_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    continue;
                case 0x03:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = op1 + op2;
                    set_add_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    continue;
                case 0x04:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = op1 + op2;
                    set_add_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    continue;
                case 0x05:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = op1 + op2;
                    set_add_flags(16, op1, op2, res);
                    set_reg16(AX, res);
                    continue;
                    
                // OR
                case 0x08:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 | op2;
                    set_bit_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    continue;
                case 0x09:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 | op2;
                    set_bit_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    continue;
                case 0x0A:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = op1 | op2;
                    set_bit_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    continue;
                case 0x0B:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = op1 | op2;
                    set_bit_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    continue;
                case 0x0C:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = op1 | op2;
                    set_bit_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    continue;
                case 0x0D:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = op1 | op2;
                    set_bit_flags(16, op1, op2, res);
                    set_reg16(AX, res);
                    continue;
                    
                // ADC
                case 0x10:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = (cf ? 1 : 0) + (op1 + op2);
                    set_add_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    continue;
                case 0x11:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = (cf ? 1 : 0) + (op1 + op2);
                    set_add_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    continue;
                case 0x12:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = (cf ? 1 : 0) + (op1 + op2);
                    set_add_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    continue;
                case 0x13:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = (cf ? 1 : 0) + (op1 + op2);
                    set_add_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    continue;
                case 0x14:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = (cf ? 1 : 0) + op1 + op2;
                    set_add_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    continue;
                case 0x15:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = (cf ? 1 : 0) + (op1 + op2);
                    set_add_flags(16, op1, op2, res);
                    set_reg16(AX, res);
                    continue;
                    
                // SBB
                case 0x18:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    continue;
                case 0x19:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    continue;
                case 0x1A:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    continue;
                case 0x1B:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    continue;
                case 0x1C:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    continue;
                case 0x1D:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = (op1 - op2);
                    res -= (cf ? 1 : 0);
                    set_sub_flags(16, op1, op2, res);
                    set_reg16(AX, res);
                    continue;
                
                // AND
                case 0x20:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 & op2;
                    set_bit_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    continue;
                case 0x21:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 & op2;
                    set_bit_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    continue;
                case 0x22:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = (op1 & op2);
                    set_bit_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    continue;
                case 0x23:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = (op1 & op2);
                    set_bit_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    continue;
                case 0x24:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = (op1 & op2);
                    set_bit_flags(8, op1, op2, res);
                    set_reg8(AL, res);
                    continue;
                case 0x25:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = op1 & op2;
                    set_bit_flags(16, op1, op2, res);
                    set_reg16(AX, res);
                    continue;
                
                // SUB
                case 0x28:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 - op2;
                    set_sub_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    continue;
                case 0x29:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 - op2;
                    set_sub_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    continue;
                case 0x2A:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = (op1 - op2);
                    set_sub_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    continue;
                case 0x2B:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = (op1 - op2);
                    set_sub_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    continue;
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
                    continue;
                
                // XOR
                case 0x30:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 ^ op2;
                    set_bit_flags(8, op1, op2, res);
                    write_rm8(modrm, res);
                    continue;
                case 0x31:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 ^ op2;
                    set_bit_flags(16, op1, op2, res);
                    write_rm16(modrm, res);
                    continue;
                case 0x32:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = op1 ^ op2;
                    set_bit_flags(8, op1, op2, res);
                    write_reg8(modrm, res);
                    continue;
                case 0x33:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = op1 ^ op2;
                    set_bit_flags(16, op1, op2, res);
                    write_reg16(modrm, res);
                    continue;
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
                    continue;
                
                // CMP
                case 0x38:
                    modrm = next_byte();
                    op1 = read_rm8(modrm);
                    op2 = read_reg8(modrm);
                    res = op1 - op2;
                    set_sub_flags(8, op1, op2, res);
                    continue;
                case 0x39:
                    modrm = next_byte();
                    op1 = read_rm16(modrm);
                    op2 = read_reg16(modrm);
                    res = op1 - op2;
                    set_sub_flags(16, op1, op2, res);
                    continue;
                case 0x3A:
                    modrm = next_byte();
                    op1 = read_reg8(modrm);
                    op2 = read_rm8(modrm);
                    res = op1 - op2;
                    set_sub_flags(8, op1, op2, res);
                    continue;
                case 0x3B:
                    modrm = next_byte();
                    op1 = read_reg16(modrm);
                    op2 = read_rm16(modrm);
                    res = op1 - op2;
                    set_sub_flags(16, op1, op2, res);
                    continue;
                case 0x3C:
                    op1 = get_reg8(AL);
                    op2 = next_byte();
                    res = (op1 - op2);
                    set_sub_flags(8, op1, op2, res);
                    continue;
                case 0x3D:
                    op1 = get_reg16(AX);
                    op2 = next_word();
                    res = op1 - op2;
                    set_sub_flags(16, op1, op2, res);
                    continue;
                case 0x90: // NOP
                case 0xE6:
                    IO.write_port(next_byte(), get_reg8(AL));
                    continue;
                default:
                    throw new UnsupportedOperationException(String.format("Opcode %02x not found!", opcode));
            }
        }
    }
}

