package InClassAssignments.Arrays;

public class AntiClockWiseReverseSpiral {
    public static void main(String[] args) {
//        TODO: guys give pull request ....
        
    Scanner sc = new Scanner(System.in);
            
          int  m = sc.nextInt();
                    
            int[][] arr = new int[m][m];
            for(int i = 0; i < m; i++){
                for(int j = 0; j < m; j++){
                    arr[i][j] = sc.nextInt();
                }
            }
int i, k = 0, l = 0, n=m;
 
        while (k < m && l < m) {
            // Print the first row from the remaining rows
            for (i = l; i < n; ++i) {
                System.out.print(arr[i][k] + " ");
            }
            k++;
 
            // Print the last column from the remaining
            // columns
            for (i = k; i < m; ++i) {
                System.out.print(arr[n-1][i] + " ");
            }
            n--;
 
            // Print the last row from the remaining rows */
            if (k < m) {
                for (i = n - 1; i >= l; --i) {
                    System.out.print(arr[i][m-1] + " ");
                }
                m--;
            }
 
            // Print the first column from the remaining
            // columns */
            if (l < n) {
                for (i = m - 1; i >= k; --i) {
                    System.out.print(arr[l][i] + " ");
                }
                l++;
            }
        }
    }

    }
    }
}
