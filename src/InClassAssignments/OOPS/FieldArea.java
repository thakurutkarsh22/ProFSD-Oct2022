package InClassAssignments.OOPS;

public class FieldArea {

    public double width;
    public double height;

    FieldArea() {

    }

    FieldArea(double width, double height) {
        setWidth(width);
        setHeight(height);
    }

    public double getWidth() {
        return this.width;
    }

    public double getHeight() {
        return this.height;
    }

    public void setWidth(double width) {
        if(width < 0) {
            this.width = 0;
        } else {
            this.width = width;
        }
    }

    public void setHeight(double height) {
        if(height < 0) {
            this.height = 0;
        } else {
            this.height = height;
        }
    }

    public double getArea() {
        return getWidth() * getHeight();
    }




}
/*
Write a class with the name FieldArea. The class needs two fields (instance variables) with name width and height of type double.

The class needs to have two constructors.
* The first constructor does not have any parameters (no- arg constructor) , in which you should print
* The second constructor has parameters width and height respectively of type double and it needs to initialize the fields. In case the width/length is less than 0 it needs to set the width field value to 0, in case the height parameter is less than 0 it needs to set the height field value to 0.

Write the following methods (instance methods):
* Method named getWidth without any parameters, it needs to return the value of width field.
* Method named getHeight without any parameters, it needs to return the value of height field.
* Method named setWidth with one parameter of type double, it needs to set the value of the width field. If the parameter is less than 0 it needs to set the width field value to 0.
* Method named setHeight with one parameter of type double, it needs to set the value of the height field. If the parameter is less than 0 it needs to set the height field value to 0.
* Method named getArea without any parameters, it needs to return the area of the wall.
 */