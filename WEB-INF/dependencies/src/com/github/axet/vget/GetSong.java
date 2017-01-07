package com.github.axet.vget;

import java.net.URL;
import java.net.MalformedURLException;
import java.io.File;
import com.github.axet.vget.VGet;

/**
 * Created by mohab on 2016-09-24.
 */
public class GetSong {
    public static void main(String[] args){
        try {
            VGet vget = new VGet(new URL(args[0]), new File("../../../songs"));
            vget.download();
        } catch(MalformedURLException e){
            e.printStackTrace();
        }
    }
}
