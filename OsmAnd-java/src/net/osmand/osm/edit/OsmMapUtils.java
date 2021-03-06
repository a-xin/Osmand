package net.osmand.osm.edit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.osmand.data.LatLon;
import net.osmand.util.MapUtils;

public class OsmMapUtils {

	public static double getDistance(Node e1, Node e2) {
		return MapUtils.getDistance(e1.getLatitude(), e1.getLongitude(), e2.getLatitude(), e2.getLongitude());
	}

	public static double getDistance(Node e1, double latitude, double longitude) {
		return MapUtils.getDistance(e1.getLatitude(), e1.getLongitude(), latitude, longitude);
	}

	public static double getDistance(Node e1, LatLon point) {
		return MapUtils.getDistance(e1.getLatitude(), e1.getLongitude(), point.getLatitude(), point.getLongitude());
	}

	public static LatLon getCenter(Entity e) {
		if (e instanceof Node) {
			return ((Node) e).getLatLon();
		} else if (e instanceof Way) {
			return getWeightCenterForNodes(((Way) e).getNodes());
		} else if (e instanceof Relation) {
			List<LatLon> list = new ArrayList<LatLon>();
			for (Entity fe : ((Relation) e).getMembers(null)) {
				LatLon c = null;
				// skip relations to avoid circular dependencies
				if (!(fe instanceof Relation)) {
					c = getCenter(fe);
				}
				if (c != null) {
					list.add(c);
				}
			}
			return getWeightCenter(list);
		}
		return null;
	}

	public static LatLon getWeightCenter(Collection<LatLon> nodes) {
		if (nodes.isEmpty()) {
			return null;
		}
		double longitude = 0;
		double latitude = 0;
		for (LatLon n : nodes) {
			longitude += n.getLongitude();
			latitude += n.getLatitude();
		}
		return new LatLon(latitude / nodes.size(), longitude / nodes.size());
	}

	public static LatLon getWeightCenterForNodes(Collection<Node> nodes) {
		if (nodes.isEmpty()) {
			return null;
		}
		double longitude = 0;
		double latitude = 0;
		int count = 0;
		for (Node n : nodes) {
			if (n != null) {
				count++;
				longitude += n.getLongitude();
				latitude += n.getLatitude();
			}
		}
		if (count == 0) {
			return null;
		}
		return new LatLon(latitude / count, longitude / count);
	}

	public static LatLon getMathWeightCenterForNodes(Collection<Node> nodes) {
		if (nodes.isEmpty()) {
			return null;
		}
		double longitude = 0;
		double latitude = 0;
		double sumDist = 0;
		Node prev = null;
		for (Node n : nodes) {
			if (n != null) {
				if (prev == null) {
					prev = n;
				} else {
					double dist = getDistance(prev, n);
					sumDist += dist;
					longitude += (prev.getLongitude() + n.getLongitude()) * dist / 2;
					latitude += (n.getLatitude() + n.getLatitude()) * dist / 2;
					prev = n;
				}
			}
		}
		if (sumDist == 0) {
			if (prev == null) {
				return null;
			}
			return prev.getLatLon();
		}
		return new LatLon(latitude / sumDist, longitude / sumDist);
	}

	public static void sortListOfEntities(List<? extends Entity> list, final double lat, final double lon) {
		Collections.sort(list, new Comparator<Entity>() {
			@Override
			public int compare(Entity o1, Entity o2) {
				return Double.compare(MapUtils.getDistance(o1.getLatLon(), lat, lon), MapUtils.getDistance(o2.getLatLon(), lat, lon));
			}
		});
	}

	public static void addIdsToList(Collection<? extends Entity> source, List<Long> ids) {
		for (Entity e : source) {
			ids.add(e.getId());
		}
	}

	public static void simplifyDouglasPeucker(List<Node> n, int zoom, int epsilon, List<Node> result) {
		if (zoom > 31) {
			zoom = 31;
		}
		ArrayList<Integer> l = new ArrayList<Integer>();
		int first = 0;
		int nsize = n.size();
		while (first < nsize) {
			if (n.get(first) != null) {
				break;
			}
			first++;
		}
		int last = nsize - 1;
		while (last >= 0) {
			if (n.get(last) != null) {
				break;
			}
			last--;
		}
		if (last - first < 1) {
			return;
		}
		// check for possible cycle
		boolean checkCycle = true;
		boolean cycle = false;
		while (checkCycle && last > first) {
			checkCycle = false;

			double x1 = MapUtils.getTileNumberX(zoom, n.get(first).getLongitude());
			double y1 = MapUtils.getTileNumberY(zoom, n.get(first).getLatitude());
			double x2 = MapUtils.getTileNumberX(zoom, n.get(last).getLongitude());
			double y2 = MapUtils.getTileNumberY(zoom, n.get(last).getLatitude());
			if (Math.abs(x1 - x2) + Math.abs(y1 - y2) < 0.001) {
				last--;
				cycle = true;
				checkCycle = true;
			}
		}
		if (last - first < 1) {
			return;
		}
		simplifyDouglasPeucker(n, zoom, epsilon, l, first, last);
		result.add(n.get(first));
		int lsize = l.size();
		for (int i = 0; i < lsize; i++) {
			result.add(n.get(l.get(i)));
		}
		if (cycle) {
			result.add(n.get(first));
		}
	}

	private static void simplifyDouglasPeucker(List<Node> n, int zoom, int epsilon, List<Integer> ints, int start, int end) {
		double dmax = -1;
		int index = -1;
		for (int i = start + 1; i <= end - 1; i++) {
			if (n.get(i) == null) {
				continue;
			}
			double d = orthogonalDistance(zoom, n.get(start), n.get(end), n.get(i));// calculate distance from line
			if (d > dmax) {
				dmax = d;
				index = i;
			}
		}
		if (dmax >= epsilon) {
			simplifyDouglasPeucker(n, zoom, epsilon, ints, start, index);
			simplifyDouglasPeucker(n, zoom, epsilon, ints, index, end);
		} else {
			ints.add(end);
		}
	}

	private static double orthogonalDistance(int zoom, Node nodeLineStart, Node nodeLineEnd, Node node) {
		LatLon p = MapUtils.getProjection(node.getLatitude(), node.getLongitude(), nodeLineStart.getLatitude(),
				nodeLineStart.getLongitude(), nodeLineEnd.getLatitude(), nodeLineEnd.getLongitude());

		double x1 = MapUtils.getTileNumberX(zoom, p.getLongitude());
		double y1 = MapUtils.getTileNumberY(zoom, p.getLatitude());
		double x2 = MapUtils.getTileNumberX(zoom, node.getLongitude());
		double y2 = MapUtils.getTileNumberY(zoom, node.getLatitude());
		double C = x2 - x1;
		double D = y2 - y1;
		return Math.sqrt(C * C + D * D);
	}

	public static boolean isClockwiseWay(Way w) {
		return isClockwiseWay(Collections.singletonList(w));
	}

	public static boolean isClockwiseWay(List<Way> ways) {
		if (ways.isEmpty()) {
			return true;
		}
		LatLon latLon = ways.get(0).getLatLon();
		double lat = latLon.getLatitude();
		double lon = 180;
		double firstLon = -360;
		boolean firstDirectionUp = false;
		double previousLon = -360;

		double clockwiseSum = 0;

		Node prev = null;
		boolean firstWay = true;
		for (Way w : ways) {
			List<Node> ns = w.getNodes();
			int startInd = 0;
			int nssize = ns.size();
			if (firstWay && nssize > 0) {
				prev = ns.get(0);
				startInd = 1;
				firstWay = false;
			}
			for (int i = startInd; i < nssize; i++) {
				Node next = ns.get(i);
				double rlon = ray_intersect_lon(prev, next, lat, lon);
				if (rlon != -360d) {
					boolean skipSameSide = (prev.getLatitude() <= lat) == (next.getLatitude() <= lat);
					if (skipSameSide) {
						continue;
					}
					boolean directionUp = prev.getLatitude() <= lat;
					if (firstLon == -360) {
						firstDirectionUp = directionUp;
						firstLon = rlon;
					} else {
						boolean clockwise = (!directionUp) == (previousLon < rlon);
						if (clockwise) {
							clockwiseSum += Math.abs(previousLon - rlon);
						} else {
							clockwiseSum -= Math.abs(previousLon - rlon);
						}
					}
					previousLon = rlon;
				}
				prev = next;
			}
		}

		if (firstLon != -360) {
			boolean clockwise = (!firstDirectionUp) == (previousLon < firstLon);
			if (clockwise) {
				clockwiseSum += Math.abs(previousLon - firstLon);
			} else {
				clockwiseSum -= Math.abs(previousLon - firstLon);
			}
		}

		return clockwiseSum >= 0;
	}

	// try to intersect from left to right
	public static double ray_intersect_lon(Node node, Node node2, double latitude, double longitude) {
		// a node below
		Node a = node.getLatitude() < node2.getLatitude() ? node : node2;
		// b node above
		Node b = a == node2 ? node : node2;
		if (latitude == a.getLatitude() || latitude == b.getLatitude()) {
			latitude += 0.00000001d;
		}
		if (latitude < a.getLatitude() || latitude > b.getLatitude()) {
			return -360d;
		} else {
			if (longitude < Math.min(a.getLongitude(), b.getLongitude())) {
				return -360d;
			} else {
				if (a.getLongitude() == b.getLongitude() && longitude == a.getLongitude()) {
					// the node on the boundary !!!
					return longitude;
				}
				// that tested on all cases (left/right)
				double lon = b.getLongitude() - (b.getLatitude() - latitude) * (b.getLongitude() - a.getLongitude())
						/ (b.getLatitude() - a.getLatitude());
				if (lon <= longitude) {
					return lon;
				} else {
					return -360d;
				}
			}
		}
	}

	/**
	 * Get the area (in m²) of a closed way, represented as a list of nodes
	 * 
	 * @param nodes
	 *            the list of nodes
	 * @return the area of it
	 */
	public static double getArea(List<Node> nodes) {
		// x = longitude
		// y = latitude
		// calculate the reference point (lower left corner of the bbox)
		// start with an arbitrary value, bigger than any lat or lon
		double refX = 500, refY = 500;
		for (Node n : nodes) {
			if (n.getLatitude() < refY)
				refY = n.getLatitude();
			if (n.getLongitude() < refX)
				refX = n.getLongitude();
		}

		List<Double> xVal = new ArrayList<Double>();
		List<Double> yVal = new ArrayList<Double>();

		for (Node n : nodes) {
			// distance from bottom line to x coordinate of node
			double xDist = MapUtils.getDistance(refY, refX, refY, n.getLongitude());
			// distance from left line to y coordinate of node
			double yDist = MapUtils.getDistance(refY, refX, n.getLatitude(), refX);

			xVal.add(xDist);
			yVal.add(yDist);
		}

		double area = 0;

		for (int i = 1; i < xVal.size(); i++) {
			area += xVal.get(i - 1) * yVal.get(i) - xVal.get(i) * yVal.get(i - 1);
		}

		return Math.abs(area) / 2;
	}
}
