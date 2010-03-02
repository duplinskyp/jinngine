/**
 * Copyright (c) 2008-2010  Morten Silcowitz.
 *
 * This file is part of the Jinngine physics library
 *
 * Jinngine is published under the GPL license, available 
 * at http://www.gnu.org/copyleft/gpl.html. 
 */
package jinngine.geometry.contact;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import jinngine.collision.GJK;
import jinngine.collision.RayCast;
import jinngine.geometry.Geometry;
import jinngine.geometry.Material;
import jinngine.geometry.SupportMap3;
import jinngine.geometry.contact.ContactGenerator.ContactPoint;
import jinngine.math.Matrix3;
import jinngine.math.Vector3;

public class ExperimentalContactGenerator implements ContactGenerator {
	
	private final double epsilon = 1e-7;
	private final double envelope = 0.125;
	private final double shell = envelope/2;
	private final SupportMap3 Sa, Sb, sco;
	private final Geometry ga,gb;
	private final GJK gjk = new GJK();
	private final RayCast raycast = new RayCast();
	private final Vector3 pa = new Vector3();
	private final Vector3 pb = new Vector3();
	private final Vector3 v = new Vector3();
	private final Vector3 n = new Vector3();
	private final List<ContactPoint> contacts = new LinkedList<ContactPoint>();
	private final List<Vector3> faceA = new ArrayList<Vector3>();
	private final List<Vector3> faceB = new ArrayList<Vector3>();
	private final double restitution;
	private final double friction;
	private final Vector3 gadisp = new Vector3();
	private final Vector3 gbdisp = new Vector3();


	
	
	public ExperimentalContactGenerator(SupportMap3 sa, Geometry ga, SupportMap3 sb, Geometry gb) {
		this.Sa = sa;
		this.Sb = sb;
		this.ga = ga;
		this.gb = gb;
		this.sco = new SupportMap3() {
			@Override
			public Vector3 supportPoint(Vector3 direction) {
				return Sa.supportPoint(direction).minus(Sb.supportPoint(direction).multiply(-1));
			}
			@Override
			public void supportFeature(Vector3 d, double epsilon, List<Vector3> face) {}
		};
		
		
		//select the smallest restitution and friction coefficients 
		if ( ga instanceof Material && gb instanceof Material) {
			double ea = ((Material)ga).getRestitution();
			double fa = ((Material)ga).getFrictionCoefficient();
			double eb = ((Material)gb).getRestitution();
			double fb = ((Material)gb).getFrictionCoefficient();

			//pick smallest values
			restitution = ea > eb ? eb : ea;
			friction    = fa > fb ? fb : fa;

		} else if ( ga instanceof Material ) {
			restitution = ((Material)ga).getRestitution();
			friction    = ((Material)ga).getFrictionCoefficient();
		} else if ( gb instanceof Material ) {
			restitution = ((Material)gb).getRestitution();
			friction    = ((Material)gb).getFrictionCoefficient();
		} else { //default values
			restitution = 0.7;
			friction = 0.5;
		}
	}
	
	@Override
	public Iterator<ContactPoint> getContacts() {
		return contacts.iterator();
	}

	@Override
	public boolean run(double dt) {
		// first we run gjk 
		gjk.run(Sa, Sb, pa, pb, envelope, epsilon, 32);
		v.assign(pa.minus(pb));
		n.assign(v.normalize());
		double d = v.squaredNorm();
		
		// if distance is below precision
		if (d < epsilon*epsilon || gjk.getState().simplexSize > 3) {
//			System.out.println("penetration case simplex " + gjk.getState().simplexSize);
			// we perform a raycast that is equivalent to
			// finding the growth distance between Sa and Sb. 
			// by that we obtain a contact normal at the 
			// intersection point. 

			// 
			ga.getLocalTranslation(gadisp);	
			gb.getLocalTranslation(gbdisp);
			Vector3 direction = ga.getBody().getPosition().add(gadisp).minus(gb.getBody().getPosition().add(gbdisp));
			// compute the largest possible starting lambda, based on the support of A-B
			// along the ray direction
//			direction.print();
			Vector3 sp = Sa.supportPoint(direction.negate()).minus(Sb.supportPoint(direction));
			double lambda = direction.dot(sp)/direction.dot(direction)-envelope/direction.norm();
			//System.out.println("lambda = " + lambda);
			//double startvalue = Sa.supportPoint(direction).minus(Sb.supportPoint(direction.multiply(-1))).dot(direction);
			raycast.run(Sa, Sb, new Vector3(), direction, pa, pb, lambda, envelope, epsilon);
			
			generate(pa, pb, pb.minus(pa).normalize(), 0.0,false);
			//System.out.println("point distance " + pa.minus(pb).norm() +"  got normal " + pb.minus(pa).normalize() );
		} else if ( d > epsilon*epsilon && d < envelope*envelope) {

			generate(pa, pb, pa.minus(pb).normalize(), 0.0,false);	
		} else {
			
			contacts.clear();
		}
		
		return true;
	}

	
	
	private void generate(Vector3 a, Vector3 b, Vector3 v, double depthr, boolean penetratingr ) {
		contacts.clear(); faceA.clear(); faceB.clear();
		Sa.supportFeature(v.multiply(-1), 0.09, faceA);
		Sb.supportFeature(v.multiply(1), 0.09, faceB);
		Vector3 direction = v.normalize();
		Vector3 midpoint = a.add(b).multiply(0.5);

		//create basis
		//Use a gram-schmidt process to create a orthonormal basis for the contact space
		Vector3 v1 = direction.copy(); Vector3 v2 = Vector3.i; Vector3 v3 = Vector3.k;    
		Vector3 t1 = v1.normalize(); 
		Vector3 t2 = v2.minus( t1.multiply(t1.dot(v2)) );

		//in case v1 and v2 are parallel
		if ( t2.abs().lessThan( Vector3.epsilon ) ) {
			v2 = Vector3.j; v3 = Vector3.k;
			t2 = v2.minus( t1.multiply(t1.dot(v2)) ).normalize();    
		} else {
			t2 = t2.normalize();
		}
		//v1 paralell with v3
		if( v1.cross(v3).abs().lessThan( Vector3.epsilon ) ) {
			v3 = Vector3.j;
		}
		//finaly calculate t3
		Vector3 t3 = v3.minus( t1.multiply(t1.dot(v3)).minus( t2.multiply(t2.dot(v3)) )).normalize();

		
		Matrix3 S = new Matrix3(t1,t2,t3);
		Matrix3 Si = S.transpose();

		
		// *)face-point case
		if ( faceB.size()>2) {
			//determine face normal
			Vector3 facenormal = faceB.get(0).minus(faceB.get(1)).cross(faceB.get(2).minus(faceB.get(1))).normalize();
			
			//for all points in face A
			for (Vector3 paw: faceA)  {
				double firstsign = 0;

				//transform p1 into contact space, and project onto tangential plane
				Vector3 p1tp = Si.multiply(paw.minus(midpoint));
				p1tp.x = 0;

				boolean inside = true;
				Vector3 pp = faceB.get(faceB.size()-1).copy();
				
				//run thru edges of face B, and check if they form a closed
				//curve around the point in face A
				for (Vector3 p2: faceB) {

					//transform and project
					Vector3 pptp = Si.multiply(pp.minus(midpoint));
					pptp.x = 0;
					Vector3 p2tp = Si.multiply(p2.minus(midpoint));
					p2tp.x = 0;

					Vector3 cr = p1tp.minus(pptp).cross(p2tp.minus(pptp));

					//first sign
					if (firstsign == 0) firstsign = cr.x;
					
					if (Math.signum(cr.x) != Math.signum(firstsign) ) {
						inside = false; 
						//System.out.println("outside");
						break;
					}

					pp = p2;
				}

				if (inside) {
					//generate point
					ContactPoint cp = new ContactPoint();
					cp.restitution = restitution;
					cp.friction = friction;

					//determine the true distance to the other face along the contact normal
					// ((d t + paw) - pp) . facenormal = 0
					// d t fn + paw fn - pp fn =  0
					// d t fn = pp fn - paw fn
					// t = (pp-paw).fn / d.fn										 
					double t = -pp.minus(paw).dot(facenormal) / direction.dot(facenormal);
					cp.distance = t;
					
					//use t to calculate to intersection point on face B
					Vector3 pbw = direction.multiply(t).add(paw);
					
					//if within envelope, generate a contact point
					if (cp.distance < envelope) {
						cp.depth = shell-cp.distance;
						cp.envelope = envelope;
						cp.paw.assign(paw);
						cp.pbw.assign(pbw);
//						cp.pa.assign(bodyA.toModel(paw));
//						cp.pb.assign(bodyB.toModel(pbw));
						cp.midpoint.assign(S.multiply(p1tp).add(midpoint));
						cp.normal.assign(direction);
						contacts.add(cp);
					} 
				} //inside
			}
		}


		//*) face - point intersection
		if (faceA.size()>2 ) {
			//determine face normal
			Vector3 facenormal = faceA.get(0).minus(faceA.get(1)).cross(faceA.get(2).minus(faceA.get(1))).normalize();

			for (Vector3 p1: faceB)  {
				double firstsign = 0;

				//transform and project
				Vector3 p1tp = Si.multiply(p1.minus(midpoint));
				p1tp.x = 0;
				
				//System.out.println("deviation="+deviation);

				boolean inside = true;
				Vector3 pp = faceA.get(faceA.size()-1).copy();
				for (Vector3 p2: faceA) {

					//transform and project
					Vector3 pptp = Si.multiply(pp.minus(midpoint));
					pptp.x = 0;
					Vector3 p2tp = Si.multiply(p2.minus(midpoint));
					p2tp.x = 0;

					Vector3 cr = p1tp.minus(pptp).cross(p2tp.minus(pptp));
					//cr.print();

					//first sign
					if (firstsign == 0) firstsign = cr.x;
					
					if (Math.signum(cr.x) != Math.signum(firstsign) ) {
						inside = false; break;
					}

					pp = p2;
				}

				if (inside) {
					//generate point
					ContactPoint cp = new ContactPoint();
					cp.restitution = restitution;
					cp.friction = friction;

					//determine the true distance to the other face along the contact normal
					// ((d t + paw) - pp) . facenormal = 0
					// d t fn + paw fn - pp fn =  0
					// d t fn = pp fn - paw fn
					// t = (pp-paw).fn / d.fn										 
					double t = pp.minus(p1).dot(facenormal) / direction.dot(facenormal);
					cp.distance = t;
					
					//use t to calculate to intersection point on face B
					Vector3 paw = direction.multiply(t).add(p1);
					
					//if within envelope, generate a contact point
					if (cp.distance < envelope) {
						cp.depth = shell-cp.distance;
						cp.envelope = envelope;
						cp.paw.assign(paw);
						cp.pbw.assign(p1);
//						cp.pa.assign(bodyA.toModel(paw));
//						cp.pb.assign(bodyB.toModel(p1));
						cp.midpoint.assign(S.multiply(p1tp).add(midpoint));
						cp.normal.assign(direction);
						contacts.add(cp);
					} 
				
				} //inside
				
			}
		}//face - point case
		
		//edge edge intersecitons  
		if (faceA.size()>1 && faceB.size()>1 ) {
			Vector3 p1p = faceA.get(faceA.size()-1);
			for (Vector3 p1: faceA) {

				Vector3 d1 = p1.minus(p1p);
				Vector3 d1t = Si.multiply(d1);
				d1t.x = 0;
				//create and project starting point for line1
				Vector3 p1pt = Si.multiply(p1p.minus(midpoint));
				p1pt.x = 0;

				
				Vector3 p2p = faceB.get(faceB.size()-1);
				for (Vector3 p2: faceB) {

					Vector3 d2 = p2.minus(p2p);
					Vector3 d2t = Si.multiply(d2);
					d2t.x = 0;
					Vector3 point = p2p.minus(p1p);
					Vector3 pointt = Si.multiply(point);
					pointt.x = 0;
			
					
//					d1t = d1t;
//					d2t = d2t;
					
					
					double det =  d1t.y * (-d2t.z) - d1t.z * (-d2t.y);
					
					if (Math.abs(det) > 1e-7) {

						double alpha = (1/det)* ((-d2t.z) * pointt.y + d2t.y * pointt.z);
						double beta  = (1/det)* ((-d1t.z) * pointt.y + d1t.y * pointt.z); 

						if ( alpha>0 && alpha <1 && beta>0 && beta<1 ) {
							//generate point
							ContactPoint cp = new ContactPoint();
							cp.restitution = restitution;
							cp.friction = friction;
							
							//find points on bodies
							Vector3 paw = p1p.add(d1.multiply(alpha));
							Vector3 pbw = p2p.add(d2.multiply(beta));
							
//							cp.distance = p1p.add(d1.multiply(alpha)).minus( p2p.add(d2.multiply(beta))).dot(direction);
							cp.distance = paw.minus(pbw).dot(direction);
							
							double d = cp.distance;

							
							//find distance of projected points
							if (d<envelope) {
								cp.depth = shell-d;
								cp.envelope = envelope;
								cp.paw.assign(paw);
								cp.pbw.assign(pbw);
//								cp.pa.assign(bodyA.toModel(paw));
//								cp.pb.assign(bodyB.toModel(pbw));
								
								cp.midpoint.assign(S.multiply(p1pt.add(d1t.multiply(alpha))).add(midpoint)  );
								cp.normal.assign(direction);
								contacts.add(cp);
							}
						 //contacts.get(contacts.size()-1).midpoint.print();


						}
					}
					
					p2p = p2;
				}
				
				
				p1p = p1;
			}
			
			
		}//edge-edge case
		

		
		//System.out.println("contacts="+contacts.size());
		//direction.print();
		
	}//generate()
}