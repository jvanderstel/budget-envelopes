/*
 * This file is a part of Budget with Envelopes.
 * Copyright 2013 Michael Howell <michael@notriddle.com>
 *
 * Budget is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Budget is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Budget. If not, see <http://www.gnu.org/licenses/>.
 */

package com.notriddle.budget;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import java.text.DateFormat;
import java.util.Date;

public class GraphFragment extends Fragment
                           implements LoaderCallbacks<Cursor> {
    public static GraphFragment newInstance() {
        return new GraphFragment();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getLoaderManager().initLoader(0, null, this);
    }

    @Override public View onCreateView(LayoutInflater inflater,
                                       ViewGroup cont, Bundle state) {
        ImageView retVal = new ImageView(getActivity());
        LayoutParams params = new ViewGroup.LayoutParams(
            LayoutParams.FILL_PARENT,
            LayoutParams.WRAP_CONTENT
        );
        retVal.setLayoutParams(params);
        return retVal;
    }

    @Override public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String time = Long.toString(System.currentTimeMillis()-5184000000l);
        SQLiteLoader retVal = new SQLiteLoader(
            getActivity(),
            new EnvelopesOpenHelper(getActivity()),
            "SELECT (SELECT sum(l2.cents) FROM log as l2 WHERE l2.envelope = l.envelope AND l2.time <= l.time), e._id, e.color, l.time, e.name FROM log as l LEFT JOIN envelopes AS e ON (e._id = l.envelope) WHERE e.color <> 0 AND l.time > "+time+" ORDER BY e._id, l.time asc"
        );
        retVal.setNotificationUri(EnvelopesOpenHelper.URI);
        return retVal;
    }

    @Override public void onLoadFinished(Loader<Cursor> ldr, Cursor data) {
        int l = data.getCount();
        data.moveToFirst();
        long maxCents = 0;
        long minCents = Long.MAX_VALUE;
        long maxTime = 0;
        long minTime = Long.MAX_VALUE;
        for (int i = 0; i != l; ++i) {
            long cents = data.getLong(0);
            if (cents > maxCents) maxCents = cents;
            if (cents < minCents) minCents = cents;
            long time = data.getLong(3);
            if (time > maxTime) maxTime = time;
            if (time < minTime) minTime = time;
            data.moveToNext();
        }

        ImageView view = (ImageView)getView();
        int cardSpacing = getActivity().getResources()
                          .getDimensionPixelSize(R.dimen.cardSpacing);
        int cardPadding = getActivity().getResources()
                          .getDimensionPixelSize(R.dimen.cardPadding);
        int textSize = cardPadding*2;
        int width = getActivity().getWindow().getWindowManager().getDefaultDisplay().getWidth()-2*(cardSpacing)-2*(cardPadding);
        Log.d("Budget", "GraphFragment.onLoadFinished(): width="+width);
        int height = getActivity().getResources()
                     .getDimensionPixelSize(R.dimen.graphHeight);
        Log.d("Budget", "GraphFragment.onLoadFinished(): height="+height);
        Bitmap chart = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        );
        Canvas chartCanvas = new Canvas(chart);
        chartCanvas.drawColor(Color.TRANSPARENT);
        Paint pen = new Paint();
        pen.setColor(0xFF000000);
        pen.setTextAlign(Paint.Align.CENTER);
        pen.setTextSize(textSize);
        Paint brush = new Paint();
        brush.setDither(true);
        brush.setHinting(Paint.HINTING_ON);
        brush.setStyle(Paint.Style.STROKE);
        float stroke = getActivity()
                       .getResources()
                        .getDimension(R.dimen.graphStroke);
        brush.setStrokeWidth(stroke);
        int currentEnvelope = -1;
        Path currentPath = null;
        float usableHeight = height-(2*stroke)-textSize-cardPadding;
        float usableWidth = width-(2*stroke)-textSize;

        data.moveToFirst();
        for (int i = 0; i != l; ++i) {
            int envelope = data.getInt(1);
            long cents = data.getLong(0);
            long time = data.getLong(3);
            float pointHeight = usableHeight-(float)((cents-minCents)*usableHeight/((double)(maxCents-minCents)))+stroke;
            float pointPosition = (float)((time-minTime)*usableWidth/((double)(maxTime-minTime)))+stroke+textSize;
            Log.d("Budget", "GraphFragment.onLoadFinished(): envelope="+envelope);
            Log.d("Budget", "GraphFragment.onLoadFinished(): envelope.name="+data.getString(4));
            Log.d("Budget", "GraphFragment.onLoadFinished(): cents="+cents);
            Log.d("Budget", "GraphFragment.onLoadFinished(): pointHeight="+pointHeight);
            Log.d("Budget", "GraphFragment.onLoadFinished(): time="+time);
            Log.d("Budget", "GraphFragment.onLoadFinished(): pointPosition="+pointPosition);
            if (envelope != currentEnvelope) {
                if (currentPath != null) {
                    currentPath.rLineTo(usableWidth, 0);
                    chartCanvas.drawPath(currentPath, brush);
                }
                int color = data.getInt(2);
                //brush = new Paint(brush);
                brush.setColor(color);
                currentEnvelope = envelope;
                currentPath = new Path();
                currentPath.moveTo(pointPosition, pointHeight);
            } else {
                currentPath.lineTo(pointPosition, pointHeight);
            }
            data.moveToNext();
        }
        if (currentPath != null) {
            currentPath.rLineTo(usableWidth, 0);
            chartCanvas.drawPath(currentPath, brush);
        }
        Path side = new Path();
        side.moveTo(textSize, height);
        side.lineTo(textSize, 0);
        chartCanvas.drawTextOnPath(getActivity().getString(R.string.envelopeDetails_balance), side, 0, 0, pen);
        Path bottom = new Path();
        bottom.moveTo(0, height-cardPadding);
        bottom.lineTo(width, height-cardPadding);
        chartCanvas.drawTextOnPath(getActivity().getString(R.string.graph_time), bottom, 0, 0, pen);
        if (maxTime != 0) {
            Date maxTimeD = new Date(maxTime);
            Date minTimeD = new Date(minTime);
            DateFormat timeFormat
             = android.text.format.DateFormat.getDateFormat(getActivity());
            pen.setTextAlign(Paint.Align.LEFT);
            chartCanvas.drawTextOnPath(timeFormat.format(minTimeD), bottom, 0, 0, pen);
            pen.setTextAlign(Paint.Align.RIGHT);
            chartCanvas.drawTextOnPath(timeFormat.format(maxTimeD), bottom, 0, 0, pen);
        }
        view.setImageBitmap(chart);
    }

    @Override public void onLoaderReset(Loader<Cursor> ldr) {
        // Do nothing.
    }
};

