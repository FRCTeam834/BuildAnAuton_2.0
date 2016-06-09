 import java.awt.Color;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import javax.swing.JOptionPane;

import visualrobot.Command;
import visualrobot.MoveAlongCurveCommand;
import visualrobot.TurnCommand;
import visualrobot.MoveStraightCommand;

public class Export {
	public static double SPEED = .3;
	
	public static ArrayList<Command> convertToCommands(Path2D path, double inchPerPixel, boolean[] backwards) {
		ArrayList<Command> toExport = new ArrayList<Command>();
		PathIterator pi = path.getPathIterator(null);
		double[] coords = new double[6];
		pi.currentSegment(coords);
		
		double lastAngle = 0;
		double lastX = coords[0];
		double lastY = coords[1];
		double currX = 0;
		double currY = 0;
		double currAngle = 0;

		pi.next();
		int i = 0;		
		SPEED = Double.parseDouble(JOptionPane.showInputDialog("Enter a speed between 0.0 and 1.0"));
		
		SPEED = SPEED > 1 ? 1.0 : SPEED; 
		SPEED = SPEED <= 0 ? 0 : SPEED; 

		
		for(; !pi.isDone(); pi.next()) {
			int type = pi.currentSegment(coords);
			
			
			
			switch(type) {
			case 1:
				currX = coords[0];
				currY = coords[1];
				
				double dX = currX-lastX;
				double dY = currY-lastY;
				
				currAngle = getCurrAngle(dX, dY, lastAngle);
				
				if(backwards[i]) {
					currAngle +=180;
				}
				
				double dAngle = currAngle - lastAngle;
				
				if(i==0) dAngle = 0;
	
				while(Math.abs(dAngle) > 180 ) {
					if(dAngle < 180) {
						dAngle += 360;
					}
					if(dAngle > 180) {
						dAngle -= 360;
					}
				}
				
				toExport.add(new TurnCommand(dAngle, SPEED, null));
	
				double distance = Math.sqrt(dX*dX+dY*dY) * inchPerPixel;
				toExport.add(new MoveStraightCommand(distance, backwards[i] ? -SPEED : SPEED, null));
				
				System.out.println(dAngle+ " degrees, " + ((backwards[i] ? -1:1)*  distance) + " inches.");
				lastX = currX;
				lastY = currY;
				lastAngle = currAngle;
				break;
			case 2:
				
				QuadCurveEquation q = new QuadCurveEquation(new QuadCurve2D.Double(lastX, lastY, coords[0], coords[1], coords[2], coords[3]));
				
				convertCurve(toExport, 0, q, lastAngle + 90, backwards[i], inchPerPixel);
				break;
			case 3:
				break;
			}
			
			i++;
		}
		
		return toExport;
	}
	
	public static void export(ArrayList<Command> commands) {
		try {
			File file = new File("auton.autr");
			ObjectOutputStream oos = new ObjectOutputStream(
									 new BufferedOutputStream(
									 new FileOutputStream(file)));
			oos.writeInt(1);
			oos.writeInt(0);
			oos.writeObject(commands);
			oos.close();
			FileInputStream inputStream = new FileInputStream(file);
			byte[] buffer = new byte[(int)file.length()];
			inputStream.read(buffer);

			URL url = new URL("ftp://anonymous@roborio-" + 
					JOptionPane.showInputDialog("Enter Team Number")
					+ "-frc.local/home/lvuser/auton.autr");
			URLConnection conn = url.openConnection();
			
			
			conn.getOutputStream().write(buffer);
			conn.getOutputStream().close();
			inputStream.close();
			
			file.delete();
			JOptionPane.showMessageDialog(null, "Exported");

			
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "Failed to Export");
		}
	}

	private static double getCurrAngle(double dX, double dY, double lastAngle) {
		double currAngle;
		if(dX == 0) {
			if(dY == 0){
				currAngle = lastAngle;
			}
			else {
				currAngle = dY < 0 ? 270 : 90;
			}
		}
		else if(dX > 0) {
			currAngle = Math.atan(dY/dX)*180.0/Math.PI;
		}
		else {
			currAngle = Math.atan(dY/dX)*180.0/Math.PI + 180.0;
		}
		
		return currAngle;
	}

	private static void convertCurve(ArrayList<Command> commands, double start, CurveEquation curve, double lastAngle, boolean backwards, double inchPerPixel) {
		double x = curve.getX(0);
		double y = curve.getY(0);
		double goal = 1.0;
		double error = 0;
		boolean lastError = true;
		boolean done = false;
		Arc2D.Double arc;
		double radius = 0;
		Point2D.Double center = null;
		
		double floor = start, ceil = 2.0;
		do {
		
			
//			if(prevCenter == null) 
				center = findCenter(x, y, curve.getX((start + goal)/2), curve.getY((start + goal)/2), curve.getX(goal), curve.getY(goal));
//			else 
//				center = findCenter2(x, y, prevCenter.x, prevCenter.y, curve.getX(goal), curve.getY(goal));

			if(center == null) return;
			radius = center.distance(x, y);
			
			error = Math.abs(center.distance(curve.getX(start+ (goal-start)/4), curve.getY(start+ (goal-start)/4)) - radius);
			error += Math.abs(center.distance(curve.getX(start+ 3*(goal-start)/4), curve.getY(start + 3*(goal-start)/4)) - radius);
			
			
			if(error > .5) {
				if(!lastError) {
					done = true;
				}
				else {
					ceil = goal;
					goal = (floor + ceil)/2;
				}
				lastError = true;
			}
			else if(goal == 1) {
				done = true;
			}
			else {
				floor = goal;
				goal = (floor + ceil)/2;				
				lastError = false;
			}
		}
		while(!done);
//		System.out.println(error);
 		
		double iAngle = Math.atan2(-curve.getY(start)+center.y, curve.getX(start)-center.x) * 180/Math.PI;
		double fAngle = Math.atan2(-curve.getY(goal)+center.y, curve.getX(goal)-center.x) * 180/Math.PI;
		double mAngle = Math.atan2(-curve.getY((goal + start)/2)+center.y, curve.getX((goal + start)/2)-center.x) * 180/Math.PI;
		
		
		if(iAngle < 0) iAngle +=360;
		if(fAngle < 0) fAngle +=360;
		if(mAngle < 0) mAngle +=360;

		double dTheta = fAngle-iAngle;
		
		if(!(mAngle < iAngle && mAngle>fAngle || mAngle >iAngle && mAngle < fAngle)) {
			if(dTheta > 0) {
				dTheta -= 360;
			}
			else if(dTheta < -0) {
				dTheta += 360;
			}
		}
		else {
			
		}
		
		double AngleError = iAngle - lastAngle;

		commands.add(new TurnCommand(AngleError, SPEED, null));
		commands.add(new MoveAlongCurveCommand(radius * inchPerPixel, SPEED, dTheta, null)); 

		System.out.println("Turning " + AngleError + " Degrees");
		System.out.println("Extent: " + dTheta + "\tRadius: " + radius * inchPerPixel);
		
		if(goal < 1) {
			convertCurve(commands, goal, curve, fAngle, backwards, inchPerPixel);
		}
		

	}
	public static Point2D.Double findCenter(double x1, double y1, double x2, double y2, double x3, double y3) {
		double midPoint1x = (x1+x2)/2;
		double midPoint1y = (y1+y2)/2;
		double midPoint2x = (x2+x3)/2;
		double midPoint2y = (y2+y3)/2;


		if(y2-y1 == 0 || y3-y2 == 0) {
			return null;
		}
		
		double slope1 = -1*(x2-x1)/(y2-y1);
		double slope2 = -1*(x3-x2)/(y3-y2);

		if(slope1 != slope2) {
		
			double x = ((midPoint2y-midPoint1y) + (slope1*midPoint1x-slope2*midPoint2x))/(slope1-slope2);
			double y = slope1*(x-midPoint1x) + midPoint1y;
			return new Point2D.Double(x, y);
		}
		return null;
	}

}
