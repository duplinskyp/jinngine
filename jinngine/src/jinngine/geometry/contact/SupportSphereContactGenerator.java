package jinngine.geometry.contact;

import java.util.Iterator;
import java.util.List;

import jinngine.collision.ExpandingPolytope;
import jinngine.collision.GJK;
import jinngine.geometry.Sphere;
import jinngine.geometry.SupportMap3;
import jinngine.geometry.contact.ContactGenerator.ContactPoint;
import jinngine.math.Matrix3;
import jinngine.math.Vector3;
import jinngine.physics.Body;

/**
 * Contact generator for Sphere-SupportMap combinations. Insted of using a sphere support map,
 * we use just the sphere centre point as supprt map for the sphere. This makes GJK behave much 
 * more regular, because the continuous shape is avoided. 
 */
public final class SupportSphereContactGenerator implements ContactGenerator {

    private static double envelope = 0.125*0.5;
	private static double shell = envelope*0.75;
	private final SupportMap3 s1;
	private final SupportMap3 pointmap;
	private final Sphere s2;
	private final Vector3 spherecentre = new Vector3();
	private final Body b1, b2;
	private final ContactPoint cp = new ContactPoint();
	private final GJK closest = new GJK();
	private boolean incontact = false;
	
	public SupportSphereContactGenerator(Body b1, SupportMap3 s1, Body b2, Sphere s2) {
		this.s1 = s1;
		this.s2 = s2;
		this.b1 = b1;
		this.b2 = b2;

		// supportmap for the sphere centre
		this.pointmap = new SupportMap3() {
			@Override
			public final Vector3 supportPoint(Vector3 direction) {
				return spherecentre;
			}
			@Override
			public final void supportFeature(Vector3 d, double epsilon,
					List<Vector3> face) {}
		};
	}
	
	@Override
	public final Iterator<ContactPoint> getContacts() {
		return new Iterator<ContactPoint>() {
			boolean done = false;
			@Override
			public boolean hasNext() {
				return (!done)&&incontact;
			}
			@Override
			public ContactPoint next() {
				done = true;
				return cp;
			}
			@Override
			public void remove() {
				// TODO Auto-generated method stub
			}			
		};
	}

	@Override
	public final boolean run(double dt) {
		// assign the centre of mass position of the sphere in world coords
		s2.getLocalTransform(new Matrix3(), spherecentre);
		Vector3.add(spherecentre, b2.state.position);

		// run gjk
		Vector3 v = new Vector3();
		closest.run(s1, pointmap, cp.paw, cp.pbw, envelope);
		
		// contact normal
		v.assign(cp.paw.minus(cp.pbw));
		cp.normal.assign(v.normalize());
		double d = v.norm();

		// world space interaction point
		cp.midpoint.assign(cp.paw.add(cp.pbw).multiply(0.5));
		
		// penetration
		if (closest.getState().simplexSize > 3  ) {
			System.out.println("Support-Sphere: penetration");
			// run EPA
			ExpandingPolytope epa = new ExpandingPolytope();
			epa.run(s1, pointmap, cp.paw, cp.pbw, closest.getState());
			v.assign(cp.paw.minus(cp.pbw));
			cp.normal.assign(v.normalize().multiply(-1));
			d = v.norm();

			// world space interaction point
			cp.midpoint.assign(cp.paw.add(cp.pbw).multiply(0.5));
			
		}	
		
		
		
		// contact within envelope
		if ( d >= 0  && d < envelope ) {
			cp.depth = shell-d;
			//cp.depth = depth-(envelope/2.0) > 0 ? depth-(envelope/2.0):0;
			incontact = true;
			return true;
		// penetration
		} else if ( d < 0){
			cp.depth = shell-d;
			//cp.depth = 0;
			incontact = true;
			return true;
		// separation
		} else {
			cp.depth = 0;
			incontact = false;
			return false;
		}
		
		
		
		
	}

}
