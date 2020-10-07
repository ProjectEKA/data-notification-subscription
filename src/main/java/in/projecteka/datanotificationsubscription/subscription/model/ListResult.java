package in.projecteka.datanotificationsubscription.subscription.model;

public class ListResult<T> {
    private final int total;
    private final T result;

    public ListResult(T result, int total) {
        this.result = result;
        this.total = total;
    }

    public int getTotal() {
        return this.total;
    }

    public T getResult() {
        return this.result;
    }
}