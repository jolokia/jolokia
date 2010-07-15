package org.jolokia.jvmagent.jdk6;

/**
 * @author roland
 * @since Mar 3, 2010
 */
public class TestMain {

    public static void main(String[] args) throws InterruptedException {
        for (int i = 1;i< 10;i++) {
            Thread thread = new Thread("Bla " + i) {
                @Override
                public void run() {
                    try {
                        sleep((long) (Math.random() * 20000));
                    } catch (InterruptedException e) {
                    }
                }
            };
            thread.start();
        }
    }
}
