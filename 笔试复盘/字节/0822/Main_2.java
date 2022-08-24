import java.util.Deque;
import java.util.LinkedList;
import java.util.Scanner;

/**
 * @author WWX
 * @date 2022/08/22 11:20
 **/

/*
    走迷宫，求出无法走出迷宫的位置的个数。
    输入是一个二维数组，.表示空地，可以走任意方向，o表示出口，UDLR分别表示该位置只能朝上下左右走。
    当走到迷宫外，游戏结束，无法达到出口

    输入：
    5 5
    .....
    .RRD.
    .U.DR
    .ULL.
    ....O
 */

    /*
        思路1：对每个位置进行dfs，计算能够找到出口的位置个数ans，结果则是n*m-ans
     */

    /*
        思路2：反向思维，从O开始用BFS找到能到达的地方就行了，最后输出n*m-ans。
                                                            ————来自牛客”offer砸死我吧，谢“大佬的思路
     */

public class Main_2 {

    static char [][] board;

    static boolean [][] visited;

    //优化点：记录每个位置最终是否可以到达终点，但是没优化出来
    static boolean [][] exit;

    static int ans;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        String [] strs = sc.nextLine().split(" ");
        int n = Integer.parseInt(strs[0]);
        int m = Integer.parseInt(strs[1]);
        board = new char[n][m];
        for (int i = 0; i < n; i++) {
            board[i] = sc.nextLine().toCharArray();
        }
        visited = new boolean[n][m];
        exit = new boolean[n][m];
        ans = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                dfs(i, j);

            }
        }
        System.out.println(n * m - ans);
    }

    public static void dfs(int x, int y) {
        if (x < 0 || x >= board.length || y < 0 || y >= board[0].length) {
            return;
        }
        //到达出口
        if (board[x][y] == 'O') {
            ans++;
            return;
        }
        //防止死循环
        if (visited[x][y] == true) {
            return;
        }
        //如果从这个格子出发最终能到出口
//        if (exit[x][y] == true) {
//            ans++;
//            return;
//        } else {
//            return;
//        }

        visited[x][y] = true;
        if (board[x][y] == 'U') {
            dfs(x - 1, y);
        } else if (board[x][y] == 'D') {
            dfs(x + 1, y);
        } else if (board[x][y] == 'L') {
            dfs(x, y - 1);
        } else if (board[x][y] == 'R'){
            dfs(x, y + 1);
        } else {
            dfs(x - 1, y);
            dfs(x + 1, y);
            dfs(x, y - 1);
            dfs(x, y + 1);
        }
        visited[x][y] = false;

    }

//    输入：
//    5 5
//    .....
//    .RRD.
//    .U.DR
//    .ULL.
//    ....O

    public void solution() {
        Scanner sc = new Scanner(System.in);
        String [] strs = sc.nextLine().split(" ");
        int n = Integer.parseInt(strs[0]);
        int m = Integer.parseInt(strs[1]);
        board = new char[n][m];
        for (int i = 0; i < n; i++) {
            board[i] = sc.nextLine().toCharArray();
        }
        Deque<int[]> queue = new LinkedList<>();
        boolean [][] visited = new boolean[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (board[i][j] == 'O') {
                    queue.offer(new int[]{i, j});
                    visited[i][j] = true;
                    break;
                }
            }
        }
        int ans = 1;
        while (!queue.isEmpty()) {
            int [] start = queue.poll();
            int x = start[0];
            int y = start[1];
            if (x - 1 >= 0 && (board[x - 1][y] == '.' || board[x - 1][y] == 'D') && !visited[x - 1][y]) {
                queue.offer(new int[]{x - 1, y});
                visited[x - 1][y] = true;
                ans++;
            }
            if (x + 1 < n && (board[x + 1][y] == '.' || board[x + 1][y] == 'U') && !visited[x + 1][y]) {
                queue.offer(new int[]{x + 1, y});
                visited[x + 1][y] = true;
                ans++;
            }
            if (y - 1 >= 0 && (board[x][y - 1] == '.' || board[x][y - 1] == 'R') && !visited[x][y - 1]) {
                queue.offer(new int[]{x, y - 1});
                visited[x][y - 1] = true;
                ans++;
            }
            if (y + 1 < m && (board[x][y + 1] == '.' || board[x][y + 1] == 'L') && !visited[x][y + 1]) {
                queue.offer(new int[]{x, y + 1});
                visited[x][y + 1] = true;
                ans++;
            }
        }
        System.out.println(n * m - ans);

    }

}

