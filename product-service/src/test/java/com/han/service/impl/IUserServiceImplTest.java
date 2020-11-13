package com.han.service.impl;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class IUserServiceImplTest {
    private static final Integer size=1000000;
    private static BloomFilter<Integer> bloomFilter;

    @Before
    public void setUp() throws Exception {
        bloomFilter=BloomFilter.create(Funnels.integerFunnel(),size,0.0001);
    }

    @Test
    public void findUserByUserId() {
        List<Integer> lists=new ArrayList<>();
        for(int i=0;i<size;i++){
            bloomFilter.put(i);
        }

        for (int i=size+10000;i<size+200000;i++){
            if (bloomFilter.mightContain(i)){
                lists.add(i);
            }
        }
        System.out.println("误判率："+lists.size());
    }

    @Test
    public void strTO() {
        System.out.println(Integer.toBinaryString(10));
        String str="abc";
        System.out.println(StrToBinstr(str));
    }

    // 将字符串转换成二进制字符串，以空格相隔
    private static String StrToBinstr(String str) {
        char[] strChar = str.toCharArray();
        String result = "";
        for (int i = 0; i < strChar.length; i++) {
            result += Integer.toBinaryString(strChar[i]) + " ";
        }
        return result;
    }

    // 将二进制字符串转换成Unicode字符串
    private static String BinstrToStr(String binStr) {
        String[] tempStr = StrToStrArray(binStr);
        char[] tempChar = new char[tempStr.length];
        for (int i = 0; i < tempStr.length; i++) {
            tempChar[i] = BinstrToChar(tempStr[i]);
        }
        return String.valueOf(tempChar);
    }

    // 将初始二进制字符串转换成字符串数组，以空格相隔
    private static String[] StrToStrArray(String str) {
        return str.split(" ");
    }
    // 将二进制字符串转换成int数组
    private static int[] BinstrToIntArray(String binStr) {
        char[] temp = binStr.toCharArray();
        int[] result = new int[temp.length];
        for (int i = 0; i < temp.length; i++) {
            result[i] = temp[i] - 48;
        }
        return result;
    }
    // 将二进制字符串转换为char
    private static char BinstrToChar(String binStr) {
        int[] temp = BinstrToIntArray(binStr);
        int sum = 0;
        for (int i = 0; i < temp.length; i++) {
            sum += temp[temp.length - 1 - i] << i;
        }
        return (char) sum;
    }
}
