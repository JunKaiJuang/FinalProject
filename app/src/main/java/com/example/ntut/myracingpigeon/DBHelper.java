package com.example.ntut.myracingpigeon;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Environment;
import android.widget.Switch;
import android.widget.Toast;

import com.readystatesoftware.sqliteasset.SQLiteAssetHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

/**
 * Created by Junkai on 2018/6/23.
 */

public class DBHelper extends SQLiteAssetHelper {
    private static final String DATABASE_NAME = "PIMS.db";
    //private static final String DATABASE_NAME = "PIMS - ancenstorTest.db";
    private static final int DATABASE_VERSION = 1;

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        SQLiteDatabase db = getReadableDatabase();
        db.execSQL("PRAGMA foreign_keys=ON");
    }

    public ArrayList<String> getSibling(int parent, String ring){

        SQLiteDatabase db = getReadableDatabase();
        String strParent = parent == 0 ? "Mother" : "Father";

        String query = String.format("select dd.Child " +
                "from Dependent D " +
                "join Dependent dd on D.%s = dd.%s " +
                "where D.Child = '%s' " +
                "and dd.Child <> D.Child", strParent, strParent, ring);
        Cursor cursor = db.rawQuery(query, null);
        cursor.moveToFirst();
        ArrayList<String> mArrayList = new ArrayList<String>();

        if(cursor.getCount() == 0) return mArrayList;

        do{
            mArrayList.add(cursor.getString(0));
        }
        while(cursor.moveToNext());

        cursor.close();
        return mArrayList;
    }

    public void fixCascadeData(){
        SQLiteDatabase db = getReadableDatabase();
        db.execSQL("delete from PInfo " +
                    "where not exists(select 1 from Pigeon p where p.Ring = PInfo.Ring)");

        db.execSQL("delete from Dependent " +
                "where not exists(select 1 from Pigeon p where p.Ring = Dependent.Child)");
    }

    public ArrayList<String> getRingList(String ring, String owner) {

        SQLiteDatabase db = getReadableDatabase();
        String ownerCondition = owner.equals("") ? "" : String.format("I.Owner = '%s' and", owner);
        String queryByRing = String.format("select P.Ring from Pigeon P join PInfo I on P.Ring = I.Ring where %s (P.Ring like '%%%s%%')", ownerCondition, ring);
        Cursor cursor = db.rawQuery(queryByRing, null);
        cursor.moveToFirst();
        ArrayList<String> mArrayList = new ArrayList<String>();

        if(cursor.getCount() == 0) return mArrayList;

        do{
            mArrayList.add(cursor.getString(0));
        }
        while(cursor.moveToNext());

        cursor.close();
        return mArrayList;
    }

    public Cursor getPigeonInfo(String ring){
        SQLiteDatabase db = getReadableDatabase();

        String queryByRing = "select P.Ring, P.Gender, P.Blood, P.Color, I.Owner from Pigeon P join PInfo I on P.Ring = I.Ring where P.Ring = '" + ring + "'";
        Cursor cursor = db.rawQuery(queryByRing, null);
        cursor.moveToFirst();
        return cursor;
    }

    public ArrayList<String> getPigeonAncestors(String ring){
        SQLiteDatabase db = getReadableDatabase();

        //找祖先
        String queryByRing =
                "with FamilyChart as(" +
                        "select D.*, 1 as TreeLevel " +
                        "from Dependent as D " +
                            "join Pigeon as P on (D.Child = P.Ring) " +
                        "where P.Ring = '" + ring + "' " +
                        "union all " +
                        "select d.* , TreeLevel +1 " +
                        "from Dependent d " +
                            "join FamilyChart fc on (fc.Mother = d.Child) or (fc.Father = d.Child) " +
                 ")select * from FamilyChart"
                ;
        Cursor cursor = db.rawQuery(queryByRing, null);
        cursor.moveToFirst();
        ArrayList<String> mArrayList = new ArrayList<String>();

        //父母
        String father, mother, grandfather, grandmother, child, extraPost;
        if(cursor.getCount() != 0) {
            father = cursor.getString(cursor.getColumnIndex("Father"));
            mother = cursor.getString(cursor.getColumnIndex("Mother"));
            father = father == null ? "未登錄" : father;
            mother = mother == null ? "未登錄" : mother;
            mArrayList.add(String.format("父: %s", father));
            mArrayList.add(String.format("母: %s", mother));

            while (cursor.moveToNext()) {
                extraPost = "";
                if (cursor.getString(cursor.getColumnIndex("TreeLevel")).equals("2")) {
                    child = cursor.getString(cursor.getColumnIndex("Child"));
                    grandfather = cursor.getString(cursor.getColumnIndex("Father"));
                    grandmother = cursor.getString(cursor.getColumnIndex("Mother"));

                    if (child.equals(mother))
                        extraPost = "外";

                    grandfather = grandfather == null ? "未登錄" : grandfather;
                    grandmother = grandmother == null ? "未登錄" : grandmother;
                    mArrayList.add(String.format("%s祖父: %s", extraPost, grandfather));
                    mArrayList.add(String.format("%s祖母: %s", extraPost, grandmother));
                }
            }
        }

        //找子嗣
        queryByRing =
                "with FamilyChart as(" +
                        "select D.*, 1 as TreeLevel " +
                        "from Dependent as D " +
                            "join Pigeon as P on (D.Mother = P.Ring) or (D.Father = P.Ring) " +
                        "where P.Ring = '" + ring + "' " +
                        "union all " +
                        "select d.* , TreeLevel +1 " +
                        "from Dependent d " +
                            "join FamilyChart fc on (fc.Child = d.Mother) or (fc.Child = d.Father) " +
                        ")select * from FamilyChart";
        cursor = db.rawQuery(queryByRing, null);
        cursor.moveToFirst();

        //子嗣
        do{
            if(cursor.getCount() == 0) break;
            if(cursor.getString(cursor.getColumnIndex("TreeLevel")).equals("1")){
                child = cursor.getString(cursor.getColumnIndex("Child"));
                mArrayList.add(String.format("子: %s", child));
            }
            else if(cursor.getString(cursor.getColumnIndex("TreeLevel")).equals("2")){
                child = cursor.getString(cursor.getColumnIndex("Child"));
                mArrayList.add(String.format("孫: %s", child));
            }
            else{
                break;
            }
        }
        while(cursor.moveToNext());

        cursor.close();
        return mArrayList;
    }

    public Boolean createPigeon(Pigeon pigeon, PInfo pInfo, Dependent dependent){
        SQLiteDatabase db = getReadableDatabase();
        db.beginTransaction();

        try {
            insertPigeon(pigeon, db);
            insertPInfo(pInfo, db);
            insertDependent(dependent, db);
            db.setTransactionSuccessful();
            return  true;
        }
        catch(SQLException e) {
            //Error in between database transaction
            throw(e);
        }
        finally {
            db.endTransaction();
        }
    }

    private void insertPigeon(Pigeon pigeon, SQLiteDatabase db){
        String ROW1 = String.format("INSERT INTO Pigeon (Ring, Gender, Blood, Color) Values ('%s', '%s', '%s', '%s')",
                pigeon.Ring, pigeon.Gender, pigeon.Blood, pigeon.Color);
        db.execSQL(ROW1);
    }

    private void insertPInfo(PInfo pInfo, SQLiteDatabase db){
        String ROW1 = String.format("INSERT INTO PInfo (Ring, Owner) Values ('%s', %s)",
                pInfo.Ring, pInfo.Owner == null ? "NULL" :  "'" + pInfo.Owner + "'");
        db.execSQL(ROW1);
    }

    private void insertDependent(Dependent dependent, SQLiteDatabase db){
        String ROW1 = String.format("INSERT INTO Dependent (Child, Father, Mother) Values ('%s', %s, %s)",
                dependent.Child
                , dependent.Father == null ? "NULL" : "'" + dependent.Father + "'"
                , dependent.Mother == null ? "NULL" : "'" + dependent.Mother + "'");
        db.execSQL(ROW1);
    }

    public void deletePigeon(String ring){
        SQLiteDatabase db = getReadableDatabase();
        String ROW1 = String.format("DELETE FROM Pigeon WHERE Ring = '%s'", ring);
        db.execSQL(ROW1);
    }

    public Boolean editPigeon(Pigeon pigeon, PInfo pInfo, Dependent dependent){
        SQLiteDatabase db = getReadableDatabase();
        db.beginTransaction();

        try {
            updatePigeon(pigeon, db);
            updatePInfo(pInfo, db);
            updateDependent(dependent, db);
            db.setTransactionSuccessful();
            return  true;
        }
        catch(SQLException e) {
            //Error in between database transaction
            throw(e);
        }
        finally {
            db.endTransaction();
        }
    }

    private void updatePigeon(Pigeon pigeon, SQLiteDatabase db){
        String ROW1 = String.format("UPDATE Pigeon SET Gender = '%s', Blood = '%s', Color = '%s' WHERE Ring = '%s'",
                pigeon.Gender, pigeon.Blood, pigeon.Color, pigeon.Ring);
        db.execSQL(ROW1);
    }

    private void updatePInfo(PInfo pInfo, SQLiteDatabase db){
        String ROW1 = String.format("UPDATE PInfo SET Owner = %s WHERE Ring = '%s'"
                , pInfo.Owner == null ? "NULL" :  "'" + pInfo.Owner + "'", pInfo.Ring);
        db.execSQL(ROW1);
    }

    private void updateDependent(Dependent dependent, SQLiteDatabase db){
        String ROW1 = String.format("UPDATE Dependent SET Father = %s, Mother = %s WHERE Child = '%s'"
                , dependent.Father == null ? "NULL" : "'" + dependent.Father + "'"
                , dependent.Mother == null ? "NULL" : "'" + dependent.Mother + "'"
                , dependent.Child);
        db.execSQL(ROW1);
    }

    public Cursor getPigeonEditData(String ring){
        SQLiteDatabase db = getReadableDatabase();

        String queryByRing = String.format(
                "select * " +
                "from Pigeon P " +
                    "join PInfo I on P.Ring = I.Ring " +
                    "join Dependent D on P.Ring = D.Child " +
                "where P.Ring = '%s'", ring);
        Cursor cursor = db.rawQuery(queryByRing, null);
        cursor.moveToFirst();
        return cursor;
    }

    public ArrayList<String> getOwnerList(){
        SQLiteDatabase db = getReadableDatabase();

        String queryByRing = "select Owner " +
                "from PInfo " +
                "Group by Owner";
        Cursor cursor = db.rawQuery(queryByRing, null);
        cursor.moveToFirst();
        ArrayList<String> mArrayList = new ArrayList<String>();

        //first data set empty string
        mArrayList.add("");

        if(cursor.getCount() == 0) return mArrayList;

        do{
            mArrayList.add(cursor.getString(0));
        }
        while(cursor.moveToNext());

        cursor.close();
        return mArrayList;
    }
}
