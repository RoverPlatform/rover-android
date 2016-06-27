package io.rover.model;

import java.util.ArrayList;

/**
 * Created by ata_n on 2016-06-16.
 */
public class Screen {

    private String mTitle;
    private ArrayList<Row> mHeaderRows;
    private ArrayList<Row> mRows;
    private ArrayList<Row> mFooterRows;

    public Screen(ArrayList<Row> rows) {
        mRows = rows;
    }

    public String getTitle() { return mTitle; }

    public void setTitle(String title) { mTitle = title; }

    public ArrayList<Row> getRows() { return mRows; }

    public ArrayList<Row> getHeaderRows() { return mHeaderRows; }

    public ArrayList<Row> getFooterRows() { return mFooterRows; }

    public void setHeaderRows(ArrayList<Row> rows) { mHeaderRows = rows; }

    public void setFooterRows(ArrayList<Row> rows) { mFooterRows = rows; }
}
