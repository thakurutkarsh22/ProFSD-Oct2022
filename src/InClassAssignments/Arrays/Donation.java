package InClassAssignments.Arrays;

import java.util.Scanner;

public class Donation {
    public static void main(String[] args) {
        Scanner scn = new Scanner(System.in);
        int n = scn.nextInt();

        int[] donationList = new int[n];
        int[] defaultersList = new int[n];
        long totalDonations = 0L;

        // fill the array donationList
        for(int i=0;i<donationList.length; i++) {
            donationList[i] = scn.nextInt();
        }

        int max = Integer.MIN_VALUE;

        // main logic to find the defaulter
        for(int i=0; i< donationList.length; i++) {
            totalDonations += donationList[i];
            max = Math.max(max, donationList[i]);

            if(i > 0) {
                if(donationList[i] >= max) {
                    defaultersList[i] = 0;
                } else {
                    defaultersList[i] = max - donationList[i];
                }
            }
            totalDonations += defaultersList[i];
        }

        //  get my outputs
        for(int i=0;i<defaultersList.length;i++) {
            System.out.print(defaultersList[i] + " ");
        }
        System.out.println();
        System.out.println(totalDonations);
    }
}
