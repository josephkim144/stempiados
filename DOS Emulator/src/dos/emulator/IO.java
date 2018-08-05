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
public class IO {
    public static void write_port(int port, int value){
        switch(port){
            // Write your code here
            default:
                throw new IllegalStateException("Unknown port write!");
        }
    }
    public static int read_port(int port, int value){
        switch(port){
            default:
                throw new IllegalStateException("Unknown port read!");
        }
    }
}
