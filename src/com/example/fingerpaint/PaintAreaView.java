package com.example.fingerpaint;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.MotionEvent;
import android.view.View;

/**
 * The Area where the user paints
 */
public class PaintAreaView extends View{

	//paths is a list of complete paths
	private ArrayList<PaintPath> paths;
	
	//curPaintPath is the path currently drawn, but not completed yet
	private PaintPath curPaintPath;
	
	//scaleMatrix is used to scale all the paths to the view size
	private Matrix scaleMatrix;
	
	//bitmap and canvas are used to draw the path as its created
	//so all the paths dont have to be redrawn during onDraw
	private Bitmap bitmap;
	private Canvas canvas;
	
	//curColor is the RGB color to use on the current path
	private int curColor;
	
	//Two paints are used, one for path one for points,because each are drawn differently
	private Paint pathPaint;
	private Paint pointPaint;
	
	//lastX and lastY are used so that we only act on new touch events
	// if they differ from previous touch event  by some threshold value
	private float lastX;
	private float lastY;
	
	private static final float THRESHOLD = 3;
	private static final float PAINT_RADIUS = 10;
	private static final int BACKGROUND_COLOR = Color.WHITE;
	
	//constructor
	public PaintAreaView(Context context){
		super(context);
		
		paths = new ArrayList<PaintPath>();
		scaleMatrix = new Matrix();
		
		pathPaint = new Paint();
		pathPaint.setAntiAlias(true);
		pathPaint.setStyle(Paint.Style.STROKE);
		pathPaint.setStrokeCap(Paint.Cap.ROUND);
		pathPaint.setStrokeJoin(Paint.Join.ROUND);
		pathPaint.setStrokeWidth(PAINT_RADIUS * 2);
		
		pointPaint = new Paint();
		pointPaint.setAntiAlias(true);
		pointPaint.setStyle(Paint.Style.FILL);
	}
	
	public void setPaintColor(int colorARGB){
		this.curColor = colorARGB;
	}
	
	public void clear(){
		canvas.drawColor(BACKGROUND_COLOR);
		paths.clear();
		invalidate();
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		
		scaleMatrix.reset();
		scaleMatrix.setScale(w, h);
		
		//create a new bitmap and associate it with the canvas
		bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
		canvas = new Canvas(bitmap);
		canvas.drawColor(BACKGROUND_COLOR);
		
		//Redraw any restored paths
		for(PaintPath p : paths){
			p.draw(canvas, scaleMatrix, pathPaint, pointPaint);
		}
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		canvas.drawBitmap(bitmap, 0, 0, null);
		
		if(curPaintPath != null){
			curPaintPath.draw(canvas, scaleMatrix, pathPaint, pointPaint);
		}
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		float x = event.getX() / getWidth();
		float y = event.getY() / getHeight();
		
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			onTouchDown(x,y);
			invalidate();
			break;
		case MotionEvent.ACTION_MOVE:
			onTouchMove(x,y);
			invalidate();
			break;
			
		case MotionEvent.ACTION_UP:
			onTouchUp();
			break;
		}		
		return true;
	}
	
	private void onTouchDown(float x, float y){
		curPaintPath = new PaintPath(curColor, x, y);
		lastX = x;
		lastY = y;
	}
	
	private void onTouchMove(float x, float y){
		float dx = Math.abs(x - lastX);
		float dy = Math.abs(y - lastY);
		
		if(dx > (THRESHOLD / getWidth()) || dy > (THRESHOLD / getHeight()) ){
			curPaintPath.addPoint(x, y);
			lastX = x;
			lastY = y;
		}
	}
	
	private void onTouchUp(){
		//draw new path onto the bitmap
		curPaintPath.draw(canvas, scaleMatrix, pathPaint, pointPaint);
		
		paths.add(curPaintPath);
		curPaintPath = null;
	}
	
	@Override
	protected Parcelable onSaveInstanceState() {
		Bundle b = new Bundle();
		b.putParcelable("super", super.onSaveInstanceState());
		b.putInt("curColor", curColor);
		b.putSerializable("paths", this.paths);
		
		return b;
	}
	
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Bundle b = (Bundle) state;
		super.onRestoreInstanceState(b.getParcelable("super"));
		this.curColor = b.getInt("curColor");
		this.paths = (ArrayList<PaintPath>) b.getSerializable("paths");
	}
	
	/**
	 * PaintPath encapsulates a paint and color, also provides a way
	 * to draw itself
	 */
	private class PaintPath implements Externalizable{
		private Path path;
		private int color;
		private ArrayList<FloatPair> points;
		
		//constructor
		public PaintPath(int color, float x, float y){
			this.color = color;
			
			this.points = new ArrayList<FloatPair>();
			this.points.add(new FloatPair(x, y));
			
			path = new Path();
			path.moveTo(x, y);
		}
		
		public void addPoint(float x, float y){
			float lastX = points.get(points.size() - 1).x;
			float lastY = points.get(points.size() - 1).y;
			path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2);
			
			points.add(new FloatPair(x, y));
		}
		
		public void draw(Canvas c, Matrix m, Paint pathPaint, Paint pointPaint){
			if(this.points.size() == 1){
				drawPoint(c, m, pointPaint);
			}else{
				drawPath(c, m, pathPaint);
			}
		}
		
		private void drawPoint(Canvas canvas, Matrix matrix, Paint paint){
			paint.setColor(this.color);
			
			float[] mValues = new float[9];
			matrix.getValues(mValues);
			
			float scaleX = mValues[Matrix.MSCALE_X];
			float scaleY = mValues[Matrix.MSCALE_Y];
			
			FloatPair first = points.get(0);
			canvas.drawCircle(first.x * scaleX, first.y * scaleY, PAINT_RADIUS, paint);
		}
		
		private void drawPath(Canvas canvas, Matrix matrix, Paint paint){
			paint.setColor(this.color);
			
			Path n = new Path();
			n.set(this.path);
			n.transform(matrix);
			canvas.drawPath(n, paint);
		}
		
		@Override
		public void readExternal(ObjectInput input) throws IOException, ClassNotFoundException {
			color = input.readInt();
			points = (ArrayList<FloatPair>) input.readObject();
		}

		@Override
		public void writeExternal(ObjectOutput output) throws IOException {
			output.writeInt(color);
			output.writeObject(points);			
		}
		
		private class FloatPair implements Serializable{
			float x,y;
			public FloatPair(float x, float y){
				this.x = x;
				this.y = y;
			}
		}
		
	}
}
