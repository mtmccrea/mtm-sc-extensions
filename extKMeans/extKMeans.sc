// Modifying Dan Stowell's k-means classifier to
// have access to data points' distance to centroid

KMeansMod : KMeans {
	var <cenDistances;

	*new { |k|
		^super.new(k).init
	}

	init {
		super.init;
		cenDistances = [];
	}

	// super override
	classify { |datum|
		var dist=inf, class=nil, adist;
		centroids.do{|cent, index|
			adist = (cent-datum); // mtm - a vector difference
			adist = (adist*adist).sum; // vector squared then summed
			if(adist < dist){
				class = index;
				dist = adist;
			}
		}
		^[class, dist]
	}

	// super override
	add { |datum|
		data = data ++ [datum];
		if(centroids.size==k){
			var class, dist;
			#class, dist = this.classify(datum);
			assignments = assignments ++ class;
			cenDistances = cenDistances ++ dist;
		}{
			assignments = assignments ++ centroids.size;
			centroids = centroids ++ [datum];
			cenDistances = cenDistances ++ 0;
		}
	}

	// super override
	reset {
		var class, dist;

		k.do{|i|
			centroids[i] = data[0].size.collect{|d| data.choose[d]}
		};
		data.do{|datum, i|
			#class, dist = this.classify(datum);
			assignments[i] = class;
			cenDistances[i] = dist;
		};
	}

	// super override
	update {
		var anyChange=true, centroidsums, centroidcounts, whichcent, dist;

		while{anyChange}{
			// Each centroid is recalculated as the mean of its datapoints
			centroidsums   = {{0}.dup(data[0].size)}.dup(k);
			centroidcounts = {0}.dup(k);
			data.do{|datum, index|
				whichcent = assignments[index];
				centroidsums[  whichcent] = centroidsums[  whichcent] + datum;
				centroidcounts[whichcent] = centroidcounts[whichcent] + 1;
			};
			centroidsums.do{ |asum, index|
				if(centroidcounts[index] != 0){
					centroids[index] = asum / centroidcounts[index]
				};
			};

			anyChange=false;
			// Datapoint classifications are checked - if any need to be updated then we'll go round again
			data.do{|datum, index|
				#whichcent, dist = this.classify(datum);
				cenDistances[index] = dist; // distance can change even if it's clustered to the same centroid

				if(whichcent != assignments[index]){
					assignments[index] = whichcent;
					anyChange = true;
				}
			};
		}; // end while
	}


	save {|filename|
		var a;

		filename = filename?? {SCMIR.tempdir++"KMeans"++".scmirZ"};

		a = SCMIRZArchive.write(filename);

		a.writeItem(k);
		a.writeItem(data);
		a.writeItem(centroids);
		a.writeItem(assignments);
		a.writeItem(cenDistances);

		a.writeClose;

	}


	load {|filename|

		var a;

		filename = filename?? {SCMIR.tempdir++"KMeans"++".scmirZ"};

		a = SCMIRZArchive.read(filename);

		k = a.readItem;
		data = a.readItem;
		centroids = a.readItem;
		assignments = a.readItem;
		cenDistances = a.readItem;

		a.close;

	}
}