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
public class HLTException extends Error {

    public HLTException() {
    }

    public HLTException(String message) {
        super(message);
    }

    public HLTException(Throwable cause) {
        super(cause);
    }

    public HLTException(String message, Throwable cause) {
        super(message, cause);
    }
}

