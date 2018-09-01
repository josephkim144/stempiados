/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dos.emulator;

import dos.emulator.cpu.CPU;
import dos.emulator.cpu.HLTException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.swing.JOptionPane;

/**
 *
 * @author jkim13
 */
public class DOSEmulator {

    public static CPU cpu;
    public static Screen screen;

    // TODO: Make this configurable
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        init();
    }

    private static void init() {
        // Initialize VM
        cpu = new CPU(1024 * 1024);
        cpu.reset();

        // Set up screen
        screen = new Screen();

        // Load BIOS binary
        loadBiosBinary();

        run();
    }

    private static String[] options = {
        "data/test.bin",
        "data/bios.bin"
    };

    private static void loadBiosBinary() {
        int i = 0;
        while (i < options.length) {
            try {
                byte[] b = Files.readAllBytes(Paths.get(options[i]));
                int length = b.length;
                int address = (1024 * 1024) - length;
                for (int j = 0; j < length; j++) {
                    cpu.write_byte(address + j, b[j]);
                }
                return;
            } catch (IOException e) {
                // OK... We couldn't find it. Ignore it and move on.
            }
            i++;
        }
        JOptionPane.showMessageDialog(null, "Cannot find BIOS binaries. Make sure a suitable IBM PC BIOS is in data/bios.bin");
    }

    public static void run() {
        int cycles = 0;
        try {
            while (true) {
                cpu.run();
                if((cycles++ % 100) == 0){
                    screen.update();
                }
            }
        } catch (HLTException e) {
            screen.update();
        }
    }
}
