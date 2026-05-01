public class MyTestUtils {
    public static  int Add(int a,int b) {
        return a + b;
    }
    public static <T extends Number> T Add(T a,T b){
        if(a instanceof  Integer i && b instanceof  Integer k){
            return (T) Integer.valueOf(i + k);
        }
        else if(a instanceof Float fa && b instanceof  Float fb){
            return (T) Double.valueOf(fa + fb);
        }
        else{
            return  (T)null;
        }
    }
}
