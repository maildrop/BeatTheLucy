package com.example;
import java.lang.*;
import java.io.IOException;
import java.util.Objects;

public final class CodePoint{

    public static void main( final String args[] ){
        final String in = "ab日本語cabc";
        System.out.println( in );
        codep( in );
    }
    public static void codep( final String in ){
        final int codePointCount = in.codePointCount(0,in.length());
        int offset = 0;
        for( final int i : new int[]{3,3,3} ){
            int cur = offset;
            final StringBuilder stringBuilder = new StringBuilder();
            for( int j = 0; j < i ; ++j ){
                final int codePoint = in.codePointAt( cur );
                stringBuilder.appendCodePoint( codePoint );
                cur = in.offsetByCodePoints( cur , 1 );
                
            }
            System.out.println( stringBuilder.toString() );
            offset = in.offsetByCodePoints( offset , i );
        }
    }
    public static void codep(final java.nio.CharBuffer cb){
        codep( new String( cb.array()) );
        return;
    }

}
