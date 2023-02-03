package net.iogilab.example;

import java.lang.*;


class SignalObject extends Object{
    private boolean signaled;
    SignalObject() {
        this.signaled = false;
    }

    public synchronized boolean signaled(){
        return signaled;
    }
    
    public synchronized void signal(){
        this.signaled = true;
        notify();
    }

    public synchronized void reset(){
        this.signaled = false;
    }
}

public final class MultipleObjects{
    private static final java.util.logging.Logger logger =
        java.util.logging.Logger.getLogger(MultipleObjects.class.getName());

    private int value_member = 0;
    private void value( int v )
    {
        value_member = v;
    }
    private int value(){
        return value_member;
    }
    
    private static final void entry_impl(){
        final MultipleObjects multipleObjects = new MultipleObjects();
        final Object obj = new Object();
        synchronized( obj ){
            var thread = new Thread( ()->{
                    synchronized( multipleObjects ){
                        synchronized( obj ){
                            obj.notify();
                        }
                        try{
                            while( multipleObjects.value() == 0 ){
                                multipleObjects.wait();
                            }
                        }catch( final InterruptedException ie ){
                            logger.severe( String.valueOf( ie ));
                        }
                    }
            });
            
            thread.start();
            try{
                obj.wait();
                synchronized( multipleObjects ){
                    thread.interrupt();
                }
                thread.join();
            }catch( InterruptedException ie ){
                logger.severe( String.valueOf( ie ) );
            }
        }
    }

    private static final void entry_impl1(){
        final SignalObject so = new SignalObject();

        final var thread = new Thread(()->{
                synchronized( so ){
                    try{
                        while( ! so.signaled() ){
                            so.wait();
                        }
                    }catch( final InterruptedException ie ){
                        logger.severe( String.valueOf( ie ));
                    }
                }
        });
        
        synchronized( so ){
            thread.start();
            try{
                Thread.sleep( 1000 );
            }catch( final InterruptedException ie ){
                logger.severe( String.valueOf( ie ));
            }
        }

        so.signal();

        try{
            thread.join();
        }catch( final InterruptedException ie ){
            logger.severe( String.valueOf( ie ));
        }
    }
    
    public static final void entry(){
        logger.entering( "net.iogilab.example.MultipleObjects" , "entry()" );
        entry_impl1();
        logger.exiting( "net.iogilab.example.MultipleObjects" , "entry()" );
        return;
    }
    
}
