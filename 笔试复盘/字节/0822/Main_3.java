import java.util.Scanner;

/**
 * @author WWX
 * @date 2022/08/22 15:31
 **/


/*
    给出一个包含通配符的句子，判断匹配的句子是否符合
    输入：
4
ad{XYZ}CDC{Y}F{x}e
adcdcefdfeffe
adcdcefdfeff
dcdcefdfeffe
adcdcfe
 */

    /*
        根据创意编写正则表达式，再去匹配
     */
public class Main_3 {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int t = Integer.parseInt(sc.nextLine());
        String tmp = sc.nextLine();
        StringBuilder res = new StringBuilder();
        boolean f = false;
        for (int i = 0; i < tmp.length(); i++) {
            if (Character.isLetter(tmp.charAt(i)) && !f) {
                res.append(tmp.charAt(i));
            } else if (tmp.charAt(i) == '{') {
                f = true;
                res.append("[a-zA-Z]*");
            } else if (tmp.charAt(i) == '}') {
                f = false;
            }
        }
        for (int i = 0; i < t; i++) {
            String s = sc.nextLine();
            if (s.matches(res.toString())) {
                System.out.println("True");
            } else {
                System.out.println("False");
            }
        }
    }

}
