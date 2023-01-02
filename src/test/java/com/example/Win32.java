package com.example;
import java.lang.*;
import java.io.IOException;

public final class Win32{
    private static final java.util.logging.Logger logger =
        java.util.logging.Logger.getLogger( Win32.class.getName() );
    public static final void main(final String args[] ){
        logger.info( "hello world" );

        final java.nio.file.Path pipePath = java.nio.file.FileSystems.getDefault().getPath("\\\\.\\pipe\\beatTheLucy");
        logger.info( String.valueOf( pipePath ) );
        try(
            final java.nio.channels.FileChannel fileChannel =
            java.nio.channels.FileChannel.open( pipePath ,
                                                java.nio.file.StandardOpenOption.CREATE_NEW,
                                                java.nio.file.StandardOpenOption.READ );
            ){
            
        }catch( final IOException ioe ){
            logger.severe( String.valueOf( ioe ));
            /*
              1月 03, 2023 4:22:33 午前 com.example.Win32 main
              重大: java.nio.file.NoSuchFileException: \\.\pipe\beatTheLucy

              WinAPI の NamedPipe は CreateNamedPipe() で作る必要があるので、FileChannel では作成出来ない。
             */
        }
        
    }
}
