package LLD.LldQuestions.MultiLevelCache.models;

public class LayerData {


//    this stores at each layer what is the read time and write time

    Double readTime;
    Double writeTime;


    public LayerData(Double readTime, Double writeTime) {
        this.readTime = readTime;
        this.writeTime = writeTime;
    }

    public Double getReadTime() {
        return readTime;
    }

    public Double getWriteTime() {
        return writeTime;
    }

}
