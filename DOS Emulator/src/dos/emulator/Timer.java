package dos.emulator;
import java.util.*;

public class Timer{
	public static void main(String[] args) {

		try {
			  while (true) {//Or any Loops
			   System.out.println("test"); //Do something
			   Thread.sleep(5000); //1000 = 1sec
			  }
			 } 
		catch (InterruptedException iex) {
			   //blank
			 }
	}
}
