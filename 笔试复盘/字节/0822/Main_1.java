/**
 * @author WWX
 * @date 2022/07/06 18:34
 **/

/*
    给定一个由01组成的字符串，定义这串数字的字串和为所有长度为2的字串的和
    如 010001可以由 01 10 00 00  01，所以和为12；
    k为可以交换数列里相邻两个数的次数（可以不用完），使得交换完之后的字串和最小

    输入： 3表示给出3组数字，第二行4表示数列长度为4，0表示k次，1010表示初始序列
    1
    4 0
    1010
 */

import java.util.Scanner;
public class Main_1 {
    //贪心：根据1在序列中的位置有三种情况，如果有1在头部，那么有10和11两种组合，有1在尾部，有01和11两种组合，如果1在中间，那么就有10和01两种组合；
    //综合来看，就只有10（头）、11（中）、01（尾）三种；
    //贪心贪在，把最后一个1放到末位，把第一个1放在首位，此时就是最小值
    //找到最后一个1的位置，假设是x，把他移动到尾部（尾部只有01这种情况），那么x移动到n-1的位置需要n - 1 - x次交换；如果n-1-x > k，那么交换到尾部，则当前序列字串和就是最小值
    //如果次数还够，找到第一个1的位置，把他交换到头部（头部只有10这种情况），假设是y，那么移动到头部需要交换y次
    //操作完后计算序列的字串和
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int T = Integer.parseInt(sc.nextLine());
        while (T-- > 0) {
            String [] strs = sc.nextLine().split(" ");
            int n = Integer.parseInt(strs[0]);
            int k = Integer.parseInt(strs[1]);
            char [] ch = sc.nextLine().toCharArray();
            int lst = 0;//序列里最后一个1的下标
            int cnt = 0;//要操作的次数
            int fst = -1;//序列里第一个1的下标
            for (int i = 0; i < n; i++) {
                if (ch[i] == '1') {
                    cnt++;
                    lst = i;
                    if (fst == -1) {
                        fst = i;
                    }
                }
            }
            //n - 1 - lst表示交换到后面需要的次数
            if (cnt >= 1 && n - 1 - lst <= k) {
                ch[lst] = '0';
                ch[n - 1] = '1';
                k -= n - 1 -lst;
                cnt--;
            }
            if (cnt >= 1 && fst <= k) {
                ch[fst] = '0';
                ch[0] = '1';
            }
            int ans = 0;
            for (int i = 0; i < n; i++) {
                if (ch[i] == '1') {
                    //最后一位为1，那么只有01这种组合
                    if (i == n - 1) {
                        ans += 1;
                        //第一位为1，只有10
                    } else if (i == 0) {
                        ans += 10;
                        //中间位有10和01
                    } else {
                        ans += 11;
                    }
                }
            }
            System.out.println(ans);
        }
    }


}
