/**
 * Copyright (c) 2010-2011 Morten Silcowitz
 *
 * This file is part of jinngine.
 *
 * jinngine is free software: you can redistribute it and/or modify it
 * under the terms of its license which may be found in the accompanying
 * LICENSE file or at <http://code.google.com/p/jinngine/>.
 */
package jinngine.physics.solver;

import java.util.List;
import jinngine.math.Vector3;
import jinngine.physics.Body;

/**
 * Implementation of the NNCG solver, or nonsmooth nonlinear Conjugate Gradient 
 * solver, as described in the Visual Computer paper "A nonsmooth nonlinear conjugate 
 * gradient method for interactive contact force problems". This method can be seen 
 * as a simple extension of the PGS method, that "reuses" some of the previous solutions
 * in each iteration, to achieve a better convergence rate. 
 * 
 * http://www.springerlink.com/content/e83432544t772126/
 * 
 * BibTex:
 * 
 * @article{1821968,
 *  author = {Silcowitz-Hansen, Morten and Niebe, Sarah and Erleben, Kenny},
 *  title = {A nonsmooth nonlinear conjugate gradient method for interactive contact force problems},
 *  journal = {Visual Computer},
 *  volume = {26},
 *  number = {6-8},
 *  year = {2010},
 *  issn = {0178-2789},
 *  pages = {893--901},
 *  doi = {http://dx.doi.org/10.1007/s00371-010-0502-6},
 *  publisher = {Springer-Verlag New York, Inc.},
 *  address = {Secaucus, NJ, USA},
 * }
 */
public class NonsmoothNonlinearConjugateGradient implements Solver {
	private int max = 10000;
//	private final double eps = 1e-7;
//	public double[] pgsiters = new double[max];
//	public double[] errors = new double[max];
	
	@Override
	public void setMaximumIterations(int n) {
		//	max =n;
	}
	
	public NonsmoothNonlinearConjugateGradient(int n ) {
		this.max = n;
//		pgsiters = new double[max];
//		errors = new double[max];
	}

	@Override
	public double solve(List<NCPConstraint> constraints, List<Body> bodies,
			double epsilon) {
		
		double rnew = 0;
		double rold = 0;
		double beta= 0;		
		int iter = 0;
		int restarts = 0;
	    epsilon = 1e-31;
		
		
		// compute external force contribution, clear direction and residual, compute b vector norm
		double bnorm = 0;
		for (NCPConstraint ci: constraints) {
			ci.Fext = ci.j1.dot(ci.body1.externaldeltavelocity)
			+ ci.j2.dot(ci.body1.externaldeltaomega)
			+ ci.j3.dot(ci.body2.externaldeltavelocity) 
			+ ci.j4.dot(ci.body2.externaldeltaomega); 
			
			ci.d = 0; ci.residual = 0;
			
			bnorm += Math.pow(ci.b+ci.Fext,2);			
		}
		bnorm = Math.sqrt(bnorm);
		final double bnorminv = 1.0/bnorm;
		
		// reset search direction
		for (Body b: bodies) {
			b.deltavelocity1.assignZero();
			b.deltaomega1.assignZero();
		}

		while (true) {	
			// copy body velocity
			for (Body bi: bodies) {
				bi.deltavelocity2.assign(bi.deltavelocity);
				bi.deltaomega2.assign(bi.deltaomega);
			}
			
			rold = rnew; rnew = 0;
			
			// use one PGS iteration to compute new residual 
			for (NCPConstraint ci: constraints) {
				// update lambda and d
				final double alpha  = beta*ci.d;
				ci.lambda += alpha;
				ci.d = alpha + ci.residual; // gradient is -r

				//calculate (Ax+b)_i 				
				final double w = ci.j1.dot(ci.body1.deltavelocity) 
				         + ci.j2.dot(ci.body1.deltaomega)
				         + ci.j3.dot(ci.body2.deltavelocity) 
				         + ci.j4.dot(ci.body2.deltaomega);
				
			    
			    double deltaLambda = -((ci.b+ci.Fext)*bnorminv+w)/(ci.diagonal);
				final double lambda0 = ci.lambda;
				
				//Clamp the lambda[i] value to the constraints
				if (ci.coupling != null) {
					//if the constraint is coupled, allow only lambda <= coupled lambda
					ci.lower = -Math.abs(ci.coupling.lambda)*ci.coupling.mu;
					ci.upper =  Math.abs(ci.coupling.lambda)*ci.coupling.mu;
					
					//use growing bounds only (disabled)
					//if the constraint is coupled, allow only lambda <= coupled lambda
//					 double lower = -Math.abs(ci.coupling.lambda)*ci.coupling.mu;					
//					ci.lower = lower<ci.lower?lower:ci.lower;					
//					double upper = Math.abs(ci.coupling.lambda)*ci.coupling.mu;
//					ci.upper =  upper>ci.upper?upper:ci.upper;

				} 

				// do projection
				final double newlambda =
					Math.max(ci.lower, Math.min(lambda0 + deltaLambda,ci.upper ));

				// update the V vector
				deltaLambda = (newlambda - lambda0);
				
				// apply to delta velocities
				Vector3.multiplyAndAdd( ci.b1, deltaLambda, ci.body1.deltavelocity );
				Vector3.multiplyAndAdd( ci.b2, deltaLambda, ci.body1.deltaomega );
				Vector3.multiplyAndAdd( ci.b3, deltaLambda, ci.body2.deltavelocity );
				Vector3.multiplyAndAdd( ci.b4, deltaLambda, ci.body2.deltaomega );
				ci.lambda += deltaLambda;

				// update residual and squared gradient
				rnew += deltaLambda*deltaLambda;
				
				ci.residual = deltaLambda;
			} //for constraints	
			
			// termination condition
			if (Math.abs(rnew) < epsilon) {
				break;
			}	

//			System.out.println("rnew="+rnew);

			
			// iteration limit
			if (iter>max || restarts > 13250 )
				break;

			//compute beta
			beta = rnew/rold;

			if ( beta > 1.0 || iter == 0 )  {
				beta = 0;
				restarts = restarts+1;
//				System.out.println("restart");
			} 
				
			for (Body bi: bodies) {
				// compute residual in body space
				Vector3.sub( bi.deltavelocity2, bi.deltavelocity);
				Vector3.sub( bi.deltaomega2, bi.deltaomega);
				Vector3.multiply( bi.deltavelocity2, -1);
				Vector3.multiply( bi.deltaomega2, -1);

				// apply to delta velocities
				Vector3.multiplyStoreAndAdd( bi.deltavelocity1, beta, bi.deltavelocity );
				Vector3.multiplyStoreAndAdd( bi.deltaomega1 , beta, bi.deltaomega );

				// add gradient from this iteration
				Vector3.add( bi.deltavelocity1, bi.deltavelocity2);
				Vector3.add( bi.deltaomega1, bi.deltaomega2);
			} 

			//iteration count
			iter = iter+1;
		} // while true

//		System.out.println("rnew="+rnew);

		
		// scale lambda in the bnorm. This is unnecessary if bnorm is set to 1
		for (NCPConstraint ci: constraints) {
			final double factor = (bnorm-1)*ci.lambda;
//			Vector3.add( ci.body1.deltavelocity, ci.b1.multiply(factor));
//			Vector3.add( ci.body1.deltaomega, ci.b2.multiply(factor));
//			Vector3.add( ci.body2.deltavelocity, ci.b3.multiply(factor));
//			Vector3.add( ci.body2.deltaomega, ci.b4.multiply(factor));
			Vector3.multiplyAndAdd( ci.b1, factor, ci.body1.deltavelocity );
			Vector3.multiplyAndAdd( ci.b2, factor, ci.body1.deltaomega );
			Vector3.multiplyAndAdd( ci.b3, factor, ci.body2.deltavelocity );
			Vector3.multiplyAndAdd( ci.b4, factor, ci.body2.deltaomega );

		}

		return 0;
	}
}
