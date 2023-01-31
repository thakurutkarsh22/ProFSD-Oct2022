package InClassAssignments.OOPS;

import Util.util;

class BubbleSort implements ISort {

    BubbleSort() {

    }

    @Override
    public int[] sort(int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            for (int j = 0; j < arr.length -1 - i; j++) {
                if(arr[j] > arr[j+1]) {
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
            }
        }
        return arr;
    }
}

class InsertionSort implements ISort {

    @Override
    public int[] sort(int[] arr) {
        for (int i = 0; i < arr.length; i++) {

            for (int j = i; j >= 1 ; j--) {

                if(arr[j] < arr[j-1]) {
                    int temp = arr[j];
                    arr[j] = arr[j - 1];
                    arr[j - 1] = temp;
                } else {
                    break;
                }
            }
        }
        return  arr;
    }
}

class MergeSort implements ISort {
    @Override
    public int[] sort(int[] arr) {
        mergeSort(arr, 0, arr.length -1);
        return arr;
    }

    private static void mergeSort(int[] arr, int left, int right) {
//        base condition
        if(left >= right) {
            return;
        }

        int mid = left + (right - left) /2;
        mergeSort(arr, left, mid);
        mergeSort(arr, mid + 1, right);
        mergeArray(arr, left, mid, right);

    }

    private static void mergeArray(int[] arr, int left, int mid, int right) {
        int p1 = left;
        int p2 = mid + 1;
        int[] ansArr = new int[right - left + 1];
        int iter = 0;

        while(p1 <= mid && p2 <= right) {
//            compare
            if(arr[p1] <= arr[p2]) {
                ansArr[iter] = arr[p1];
                iter++;
                p1++;
            } else {
                ansArr[iter] = arr[p2];
                iter++;
                p2++;
            }
        }

        while(p1 <=mid) {
            ansArr[iter] = arr[p1];
            iter++;
            p1++;
        }

        while(p2 <= right) {
            ansArr[iter] = arr[p2];
            iter++;
            p2++;
        }


//        change original array

        for (int i = left; i <= right; i++) {
            arr[i] = ansArr[i - left];
        }


    }
}

class SelectionSort implements ISort {

    @Override
    public int[] sort(int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            int lowest = i;

            for(int j = i + 1; j < arr.length; j++) {
                if(arr[j] < arr[lowest]) {
                    lowest = j;
                }
            }

//            swap lowest to i
            int temp =  arr[lowest];
            arr[lowest] = arr[i];
            arr[i] = temp;
        }
        return arr;
    }
}

interface ISort{
    public int[] sort(int[] arr);
}


class client {
    public static void main(String[] args) {
        int[] arr = {1,9,2,7,3,8,4,1,0,88, 100 , 93, 1000, 1890, 1024, 2048, 1190, 7};
        BubbleSort bs = new BubbleSort();
        int[] sortedArr = bs.sort(arr);
        util.printArrayInt(sortedArr, "sortedArr Bubble");

        InsertionSort ins = new InsertionSort();
        int[] sortedArrIns = ins.sort(arr);
        util.printArrayInt(sortedArrIns, "sortedArr insertion");

        MergeSort mergeSort = new MergeSort();
        int[] mergeSortAns = mergeSort.sort(arr);
        util.printArrayInt(mergeSortAns, "sortedArr mergeSort");

        SelectionSort ss = new SelectionSort();
        int[] ssAns = ss.sort(arr);
        util.printArrayInt(ssAns, "sortedArr selectionSort");
    }
}
