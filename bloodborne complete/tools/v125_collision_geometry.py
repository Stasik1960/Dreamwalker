"""Offline geometry inputs for bounded NEW default colliders; no runtime raster."""
import math
import numpy as np
from gallery_platform_plan import corners, rotate


def solid_parts(pose, models, profile='base', panel_tolerance=0.):
    solids = []
    for part in pose['parts']:
        if part.get('emissive'):
            continue
        model = part.get('altModel', part['model']) if profile == 'alt' else part['model']
        for element in models.elements(model):
            if not element.get('faces'):
                continue
            dimensions = [element['to'][i] - element['from'][i] for i in range(3)]
            zero = [i for i in range(3) if dimensions[i] < 1e-7]
            if zero and (len(zero)!=1 or not panel_tolerance):
                continue
            primitive = {'from':list(element['from']), 'to':list(element['to'])}
            local = element.get('rotation')
            # Minecraft uses the 45-degree rescale for every supported angle
            # other than +/-22.5, including a rescaled zero-degree element.
            axis = 'xyz'.index(local['axis']) if local else None
            scale = 1 / math.cos(math.radians(22.5 if abs(local['angle']) == 22.5 else 45)) if local and local.get('rescale') else 1.
            if zero:
                tolerance = panel_tolerance / (scale if zero[0] != axis else 1.)
                primitive['from'][zero[0]] -= tolerance/2
                primitive['to'][zero[0]] += tolerance/2
            points = list(corners(primitive))
            if local:
                if local.get('rescale'):
                    points = [[local['origin'][i] + (p[i] - local['origin'][i]) * (1 if i == axis else scale)
                               for i in range(3)] for p in points]
                points = [rotate(p, axis, local['angle'], local['origin']) for p in points]
            moved = []
            for point in points:
                point = rotate(point, 1, part.get('extraYaw', 0), part.get('extraPivot', [8, 8, 8]))
                point = rotate(point, 0, -part.get('pitch', 0), part.get('pivot', [8, 8, 8]))
                point = rotate(point, 1, -part.get('yaw', 0), part.get('pivot', [8, 8, 8]))
                moved.append([point[i] + part.get('offset', [0, 0, 0])[i] for i in range(3)])
            if zero:
                # A formerly nonpassable structural panel may have a small
                # support tolerance, never extend physics to unrelated foliage.
                hull_low = [min(value['from'][i] for value in pose['collision']) for i in range(3)]
                hull_high = [max(value['to'][i] for value in pose['collision']) for i in range(3)]
                if any(min(point[i] for point in moved)<hull_low[i]-panel_tolerance-1e-6
                       or max(point[i] for point in moved)>hull_high[i]+panel_tolerance+1e-6 for i in range(3)):
                    continue
            solids.append(np.asarray(moved, dtype=float))
    return solids


def oriented(solids, yaw):
    angle = math.radians(yaw * 45)
    c, s = math.cos(angle), math.sin(angle)
    if yaw % 2 == 0:
        c, s = round(c), round(s)
    matrix = np.array([[c, 0, -s], [0, 1, 0], [s, 0, c]])
    centre = np.array([8., 8., 8.])
    return [(points - centre) @ matrix.T + centre for points in solids]


def envelope(solids):
    points = np.concatenate(solids)
    return np.min(points, axis=0), np.max(points, axis=0)


def solid_volume(points):
    return abs(np.linalg.det(np.column_stack((points[4] - points[0], points[2] - points[0], points[1] - points[0]))))


def geometry_key(solids):
    return tuple(sorted(set(tuple(np.round(points, 6).ravel()) for points in solids)))


def box(low, high):
    return {'from': [round(float(v), 8) for v in low], 'to': [round(float(v), 8) for v in high]}


def inside(points, solids):
    result = np.zeros(len(points), dtype=bool)
    for solid in solids:
        origin = solid[0]
        low, high = np.min(solid, axis=0), np.max(solid, axis=0)
        candidates = np.flatnonzero(~result & np.all((points >= low - 1e-7) & (points <= high + 1e-7), axis=1))
        if not len(candidates):
            continue
        basis = np.column_stack((solid[4] - origin, solid[2] - origin, solid[1] - origin))
        coordinates = (points[candidates] - origin) @ np.linalg.inv(basis).T
        result[candidates] |= np.all((coordinates >= -1e-7) & (coordinates <= 1 + 1e-7), axis=1)
    return result


def box_solids(boxes):
    return [np.asarray(list(corners(value)), dtype=float) for value in boxes]


def axis_aligned(solids):
    for points in solids:
        basis = np.column_stack((points[4]-points[0], points[2]-points[0], points[1]-points[0]))
        if any(np.count_nonzero(np.abs(basis[:, i]) > 1e-6) != 1 for i in range(3)):
            return False
    return True


def largest_rectangle(values):
    """Largest all-true rectangle in one analysis slice; no runtime grid exists."""
    heights = np.zeros(values.shape[1], dtype=int)
    best = (0, None)
    for row, vector in enumerate(values):
        heights = np.where(vector, heights+1, 0)
        stack = []
        for column in range(len(heights)+1):
            height = int(heights[column]) if column < len(heights) else 0
            start = column
            while stack and stack[-1][1] > height:
                start, previous = stack.pop()
                area = previous * (column-start)
                if area > best[0]:
                    best = (area, (row-previous+1, row+1, start, column))
            if not stack or stack[-1][1] < height:
                stack.append((start, height))
    return best[1]


def coarse_inside_mask(solids):
    low, high = envelope(solids)
    spans = high-low
    thin = int(np.argmin(spans))
    axes = [axis for axis in range(3) if axis != thin] + [thin]
    # Resolution is an offline fitting aid. Output is <=12 large cuboids.
    counts = [24, 24, min(8, max(1, math.ceil(spans[thin]/4)))]
    edges = [np.linspace(low[axis], high[axis], count+1) for axis, count in zip(axes, counts)]
    shape = tuple(counts)
    indices = np.indices(shape).reshape(3, -1).T
    mask = np.ones(len(indices), dtype=bool)
    for corner in [(a,b,c) for a in (0,1) for b in (0,1) for c in (0,1)] + [(0.5,0.5,0.5)]:
        points = np.empty((len(indices), 3))
        for grid_axis, world_axis in enumerate(axes):
            points[:, world_axis] = low[world_axis] + (indices[:, grid_axis]+corner[grid_axis])*spans[world_axis]/counts[grid_axis]
        mask &= inside(points, solids)
        if not np.any(mask):
            break
    return mask.reshape(shape), edges, axes


def largest_inscribed_single(solids):
    """Fallback inside a real primitive, preserving thin models without expansion."""
    import itertools
    candidates = []
    for points in sorted(solids, key=solid_volume, reverse=True)[:8]:
        centre = np.mean(points, axis=0)
        basis = np.column_stack((points[4]-points[0], points[2]-points[0], points[1]-points[0]))
        matrix = np.abs(np.linalg.inv(basis))
        extent = (np.max(points,axis=0)-np.min(points,axis=0))/2
        for order in itertools.permutations(range(3)):
            half = extent / 3
            half *= min(1., .4999999/max(matrix@half))
            for axis in order:
                other = matrix@half - matrix[:,axis]*half[axis]
                constraints = [(.4999999-other[row])/matrix[row,axis] for row in range(3) if matrix[row,axis]>1e-10]
                half[axis] = min(constraints)
            if np.all(half > 1e-7):
                candidates.append((float(np.prod(half)), box(centre-half, centre+half)))
    return [max(candidates, key=lambda value:value[0])[1]] if candidates else []


def distributed_primitive_boxes(solids, budget):
    candidates = []
    for solid in solids:
        candidates.extend(largest_inscribed_single([solid]))
    unique = {tuple(value['from']+value['to']):value for value in candidates}
    candidates = list(unique.values())
    result = []
    while candidates and len(result)<budget:
        scored = []
        for candidate in candidates:
            lo,hi=np.asarray(candidate['from']),np.asarray(candidate['to'])
            points = np.array([[lo[i]+(hi[i]-lo[i])*t[i] for i in range(3)]
                               for t in [(a,b,c) for a in (.1,.5,.9) for b in (.1,.5,.9) for c in (.1,.5,.9)]])
            covered=np.zeros(len(points),dtype=bool)
            for previous in result:
                covered |= np.all((points>=previous['from']) & (points<=previous['to']),axis=1)
            score=float(np.prod(hi-lo))*(1-float(np.mean(covered)))
            scored.append((score,candidate))
        score,selected=max(scored,key=lambda row:row[0])
        if score<=1e-6:
            break
        result.append(selected)
        candidates.remove(selected)
    return result


def validate_inside_boxes(values, solids):
    """Retain broad unions only when their interior is occupied as well.

    A mask corner can straddle a tiny gap between authored primitives. Replace
    such a union by the largest real inscribed primitive intersection, never
    grow it or turn a gap into solid geometry.
    """
    import itertools
    output = []
    primitive_boxes = None
    for value in values:
        low,high=np.asarray(value['from']),np.asarray(value['to'])
        points=np.asarray([low+(high-low)*np.asarray(p) for p in itertools.product((0.,.25,.5,.75,1.),repeat=3)])
        if np.all(inside(points,solids)):
            output.append(value)
            continue
        if primitive_boxes is None:
            primitive_boxes=[candidate for solid in solids for candidate in largest_inscribed_single([solid])]
        candidates=[]
        for primitive in primitive_boxes:
            a,b=np.maximum(low,primitive['from']),np.minimum(high,primitive['to'])
            if np.all(b-a>1e-7):candidates.append(box(a,b))
        if candidates:
            output.append(max(candidates,key=lambda candidate:np.prod(np.asarray(candidate['to'])-candidate['from'])))
    return output


def fit_large_boxes(solids, budget=9, coverage=.86):
    """Few connected coarse interior boxes, never a filled outer AABB."""
    mask, edges, axes = coarse_inside_mask(solids)
    total = int(np.count_nonzero(mask))
    safe_volume = total*math.prod(edge[1]-edge[0] for edge in edges)
    primitive_volume = sum(solid_volume(solid) for solid in solids)
    if not total or safe_volume < primitive_volume*.25:
        return distributed_primitive_boxes(solids,budget), {'safeAnalysisCells':total, 'fallback':'few_actual_structural_primitives',
                                                           'safeMaskToSummedPrimitiveVolume':round(safe_volume/primitive_volume,6)}
    remaining = mask.copy()
    emitted = []
    while len(emitted) < budget and np.count_nonzero(remaining) > total*(1-coverage):
        candidates = []
        for layer in range(remaining.shape[2]):
            rectangle = largest_rectangle(remaining[:,:,layer])
            if rectangle is None:
                continue
            a,b,c,d = rectangle
            first,last = layer,layer+1
            while first>0 and np.all(remaining[a:b,c:d,first-1]):
                first -= 1
            while last<remaining.shape[2] and np.all(remaining[a:b,c:d,last]):
                last += 1
            candidates.append(((b-a)*(d-c)*(last-first), (a,b,c,d,first,last)))
        if not candidates:
            break
        _, (a,b,c,d,first,last) = max(candidates, key=lambda value:value[0])
        lower,upper = [0.]*3,[0.]*3
        for grid_axis, lo, hi in [(0,a,b),(1,c,d),(2,first,last)]:
            lower[axes[grid_axis]],upper[axes[grid_axis]] = edges[grid_axis][lo],edges[grid_axis][hi]
        emitted.append(box(lower,upper))
        remaining[a:b,c:d,first:last] = False
    # No outward snapping: coordinates remain exact fitted interior boundaries.
    proposed_count=len(emitted)
    emitted=validate_inside_boxes(emitted,solids)
    return emitted, {'safeAnalysisCells':total, 'coveredSafeCells':total-int(np.count_nonzero(remaining)),
                     'analysisMaskCoveredSafeFractionBeforeInteriorRefinement':round(1-np.count_nonzero(remaining)/total,6),
                     'proposedCuboidsBeforeInteriorRefinement':proposed_count,'emittedCuboidsInteriorVerified':len(emitted)}
