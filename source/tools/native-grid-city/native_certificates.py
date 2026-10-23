"""Root-reviewed, data-only finite retention certificates and source lineage.

No world file is opened. A certificate names complete original owners and
member points explicitly; another owner never becomes a candidate implicitly.
"""
import sqlite3
from pathlib import Path
import native_resume as n

TABLES=('substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots','bridge_cells','protected_cells','placements')

# This is a migration-only occupancy proof, not a placement/clearance test.
# Source collision/visual intersections are copied by the existing exact tail.
OVERLAP_CAPABILITIES_SHA='4631f03fe354733a230dab9d5bbffc339a3cbf3fefb9362544199569685dc461'
OVERLAP_BASELINE_SHA='40cecfb7e5cf783a762e350fc1864518c39abff177f6f025cb94892a181fbf51'
OVERLAP_NORMAL_BE={'id','x','y','z','keepPacked','payload','resident','owners','OwnerDataV1'}
LOCAL_ORIGINAL_REASON='automatic_direct_original_native_member_overlap'
NATIVE_TREE_FOOTPRINT_SHA='7de51902eb54e31e9473fec40274dfb3587f3b67a82065629c9c89b5f0cf6724'
ORIGINAL_NATIVE_CATALOG_SHA='3a9d3325e31818484689e2cc4d22fa63c6168338fa721ee3315935ddee9124f2'


def source_visual_member_identity(m,db):
    """The already accepted discovery identity, not a new full database hash."""
    binding=m.meta(db,'binding');path=m.checked(Path(binding['oldPlan'])/'plan.sqlite')
    attached=[row[2] for row in db.execute('PRAGMA database_list') if row[1]=='old']
    m.require(len(attached)==1 and m.checked(attached[0])==path,'Owner-member cache belongs to another old database')
    stat=path.stat()
    m.require(stat.st_size==binding['oldDatabaseBytes'] and stat.st_mtime_ns==binding['oldDatabaseMtimeNs'],'Immutable source database identity changed')
    return {'path':str(path),'bytes':stat.st_size,'mtimeNs':stat.st_mtime_ns,
            'copyManifestSha256':binding.get('copyManifestSha256'),'plannerBindingSha256':binding.get('plannerBindingSha256')}


def prepare_source_visual_member_index(m,db):
    """One cache-only atomic transaction before any native pending batch.

    The immutable discovery is read twice, once per table. Positions only are
    copied into the current plan. Full original rows keep their original PK
    authority and are fetched individually by the bounded owner getter.
    """
    m.require(not db.in_transaction,'Prepare owner-member index outside a native/selection transaction')
    identity=source_visual_member_identity(m,db);previous=m.meta(db,'sourceVisualMemberIndex')
    if previous:
        m.require(previous.get('schemaVersion')==1 and previous.get('kind')=='DERIVED_SOURCE_OWNER_MEMBER_POSITIONS' and previous.get('sourceIdentity')==identity,'Derived owner-member index binding changed')
        m.require(set(previous['copiedCounts'])=={'stored_contributions','members'} and previous['rows']==sum(previous['copiedCounts'].values()),'Derived source member counts changed')
        m.require(db.execute('SELECT count(*) FROM source_visual_owner_members').fetchone()[0]==previous['rows'],'Derived owner-member index is incomplete')
        return
    db.execute('BEGIN IMMEDIATE')
    try:
        m.require(not m.meta(db,'sourceVisualMemberIndex') and db.execute('SELECT count(*) FROM source_visual_owner_members').fetchone()[0]==0,'Unbound/partial owner-member cache cannot be adopted')
        copied={}
        for table in ('stored_contributions','members'):
            before=db.total_changes
            db.execute('INSERT INTO source_visual_owner_members SELECT dim,uuid,?,x,y,z FROM old.'+table,(table,))
            copied[table]=db.total_changes-before
        m.require(source_visual_member_identity(m,db)==identity,'Old source changed while copying member positions')
        rows=db.execute('SELECT count(*) FROM source_visual_owner_members').fetchone()[0]
        m.require(rows==sum(copied.values()),'Derived member-position copy is incomplete')
        m.meta(db,'sourceVisualMemberIndex',{'schemaVersion':1,'kind':'DERIVED_SOURCE_OWNER_MEMBER_POSITIONS','sourceIdentity':identity,'copiedCounts':copied,'rows':rows})
        db.commit()
    except BaseException:
        db.rollback();raise


class SourceVisualReservations:
    """Lazy source-owned domains and atomic, deduplicated encounter decisions.

    Raw exported cells are a conservative runtime envelope (selection/optical
    carriers included), never pose_cells' clipped visual volume. A point inside
    the envelope cannot receive the positive visual-only decision. No owner is
    discovered by walking another owner's footprint.
    """
    def __init__(self,m,db,exports=None):
        self.m,self.db,self.exports=m,db,exports
        self.chunks=m.collections.OrderedDict();self.domains=m.collections.OrderedDict()
        self.resources={};self.geometry={};self.strict=None
        self.bindings={key:m.meta(db,key) for key in ('binding','classification','poseFragments','doorFootprintGuard','payloadDecisions','functionalGuestPreflight')}
        self.files=None
        self.journal_tables=bool(db.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='source_visual_domains'").fetchone())
        self.member_index=m.meta(db,'sourceVisualMemberIndex')
        m.require(exports is None or self.member_index,'Live native composition requires its prepared owner-member index')

    def close(self):
        owned=self.__dict__.pop('_source_jar',None)
        if owned is not None:owned[1].close()

    def domain_equal(self,actual,expected):
        """Only an explicitly bound unchanged-export append may differ."""
        if actual==expected:return True
        m,db=self.m,self.db
        if not isinstance(actual,dict) or not isinstance(expected,dict):return False
        bindings=expected.get('bindings');current=self.bindings.get('poseFragments')
        if not isinstance(bindings,dict) or not isinstance(current,dict):return False
        historical=bindings.get('poseFragments')
        if not isinstance(historical,dict) or set(historical)!={'path','sha256'} or set(current)!={'path','sha256'}:return False
        if historical['path']!=current['path'] or historical==current or m.meta(db,'poseFragments')!=current:return False
        normalized={**expected,'bindings':{**bindings,'poseFragments':current}}
        if normalized!=actual:return False
        reference=m.meta(db,'nativeFragmentAppendLineage')
        m.require(isinstance(reference,dict) and set(reference)=={'path','sha256'},'A historical domain requires an explicitly bound strict native append')
        token=(reference['path'],reference['sha256'],historical['path'],historical['sha256'],current['sha256'])
        if self.__dict__.get('_domain_append_checked')!=token:
            proof=bound(m,reference)
            m.require(proof['status']=='PASS' and proof['mode']=='STRICT_ADDITIVE_NATIVE_EXPORT_LINEAGE' and proof['beforeFragmentsSha256']==historical['sha256'] and proof['afterFragmentsSha256']==current['sha256'] and all(proof.get(key) is True for key in ('posesUnchanged','fragmentsUnchanged','existingNativeRowsUnchanged','existingNativeVisualFragmentsUnchanged')) and m.sha(current['path'])==current['sha256'],'Domain append changed prior source/shape/visual rows or the current export store')
            self._domain_append_checked=token
        return True

    def __del__(self):
        try:self.close()
        except Exception:pass

    def owner_member_rows(self,dim,uid,table):
        m,db=self.m,self.db;m.require(table in ('stored_contributions','members'),'Unknown source member table')
        if not self.member_index:
            # Honest read-only pre-admission wire fallback; live composition
            # prepares the derived index before constructing this context.
            return n.row_dict(db,'SELECT * FROM old.'+table+' WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',(dim,uid))
        if '_member_index_checked' not in self.__dict__:
            m.require(self.member_index.get('schemaVersion')==1 and self.member_index.get('kind')=='DERIVED_SOURCE_OWNER_MEMBER_POSITIONS' and self.member_index['sourceIdentity']==source_visual_member_identity(m,db),'Owner-member lookup has another source binding')
            self._member_index_checked=True
        positions=db.execute('SELECT x,y,z FROM source_visual_owner_members WHERE dim=? AND uuid=? AND sourceTable=? ORDER BY x,y,z LIMIT 4097',(dim,uid,table)).fetchall()
        m.require(len(positions)<=4096,'Retained owner member bound exceeded');rows=[]
        for at in positions:
            original=n.row_dict(db,'SELECT * FROM old.'+table+' WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',(dim,*at,uid))
            m.require(len(original)==1,'A derived member position lost its exact original PK row');rows.append(original[0])
        return rows

    def classification(self):
        if '_classification' not in self.__dict__:self._classification=bound(self.m,self.bindings['classification'])
        return self._classification

    def local_native_domains(self):
        """Committed original-native identities, loaded once per batch context."""
        if '_local_native_domains' not in self.__dict__:
            self._local_native_domains={}
            if self.m.meta(self.db,'sourceLocalRetentionJournal'):
                for encoded,digest in self.db.execute('SELECT data,digest FROM source_local_retentions ORDER BY sequence'):
                    event=self.m.json.loads(encoded);self.m.require(self.m.digest(event)==digest,'Committed local original journal changed')
                    for domain in event['originalDomains']:
                        obj=domain['object']
                        if obj['kind']=='substrate' or domain.get('nativeRootIdentity') or domain['mechanism']=='legacy_static_recipe':
                            self.m.require(domain['mechanism'] in ('legacy_static_recipe','source_original_snapshot'),'Unknown local original-native mechanism')
                            self._local_native_domains[(obj['dim'],obj['uuid'])]=domain
        return self._local_native_domains

    def local_native_cells(self):
        if '_local_native_cells' not in self.__dict__:
            self._local_native_cells={}
            if self.m.meta(self.db,'sourceLocalRetentionJournal'):
                for encoded, in self.db.execute('SELECT data FROM source_local_retentions ORDER BY sequence'):
                    for row in self.m.json.loads(encoded).get('originalNativeCells',[]):self._local_native_cells[(row['dimension'],*row['position'])]=row
        return self._local_native_cells

    def original_zones(self):
        if '_original_zones' not in self.__dict__:
            self._original_zones={}
            if self.m.meta(self.db,'sourceLocalRetentionJournal'):
                for encoded, in self.db.execute('SELECT data FROM source_local_retentions ORDER BY sequence'):
                    zone=self.m.json.loads(encoded).get('sourceZone')
                    if zone:self._original_zones[(zone['dimension'],*zone['minimum'])]=zone
        return self._original_zones

    def zone_snapshot(self,dim,minimum):
        m=self.m;minimum=tuple(minimum);key=(dim,*minimum);memo=self.__dict__.setdefault('_zone_snapshots',{})
        m.require(all(value%16==0 for value in minimum),'Original section must be aligned in all three axes')
        if key not in memo:
            states=[];bes=[];chunk=None
            for y in range(minimum[1],minimum[1]+16):
                for z in range(minimum[2],minimum[2]+16):
                    for x in range(minimum[0],minimum[0]+16):
                        at=(x,y,z);state,be,proof=self.source_point(dim,at);states.append(state)
                        m.require(chunk is None or chunk==proof,'Aligned source section crossed its immutable chunk binding');chunk=proof
                        if be is not None:bes.append({'position':list(at),'typedBeDigest':self.source_be_digest(dim,at,be,proof)})
            memo[key]={'schemaVersion':1,'dimension':dim,'minimum':list(minimum),'maximum':[value+15 for value in minimum],'sourceChunk':chunk,'sourceStatesDigest':m.digest(states),'typedBlockEntities':bes,'sourceBinding':m.meta(self.db,'binding')}
        return memo[key]

    def operation_offsets(self,pose,spec):
        m=self.m;essential=spec.get('essentialMask',[[0,0,0]]);support=spec.get('support',{'required':False})
        m.require(isinstance(essential,list) and isinstance(support,dict),'Unknown essential/support source schema')
        result={tuple(at) for at in essential}|{(0,0,0)}
        rotation=int(pose.get('properties',{}).get('rotation','0'));angle=rotation*m.math.pi/4;rotated=set()
        for x,y,z in result:
            a=x*m.math.cos(angle)-z*m.math.sin(angle);b=x*m.math.sin(angle)+z*m.math.cos(angle)
            m.require(abs(a-round(a))<1e-6 and abs(b-round(b))<1e-6,'Unknown fractional essential/support ownership transform')
            rotated.add((round(a),y,round(b)))
        # CompositeRuntime checks root.add(spec.supportOffset), not its rotation.
        if support.get('required'):
            m.require('offset' in support,'Required support has no exact source offset');rotated.add(tuple(support['offset']))
        return rotated

    def door_operations_at(self,dim,at):
        """Known endpoint and support roots only; no world/district walk.

        The small existing unique-pose report supplies offsets. Root lookups use
        object_root at the exact implied coordinate, including other doors whose
        support cell has no protected_cells row at this point.
        """
        m,db=self.m,self.db
        if '_door_offsets' not in self.__dict__:
            self._door_offsets={};reference=self.bindings['doorFootprintGuard']
            if reference:
                report=bound(m,reference)
                m.require(report.get('status')=='PASS' and report.get('nativeDescriptor') is True and report.get('baselineModJarSha256')==OVERLAP_BASELINE_SHA,'Door operating-domain authority changed')
                for row in report['poses']:
                    encoded=db.execute('SELECT data FROM poses WHERE key=?',(row['key'],)).fetchone();m.require(encoded,'Unknown door SourcePose')
                    pose=m.json.loads(encoded[0]);_,_,spec,_=self.authority(pose)
                    m.require({p['open'] for p in row['states']}=={False,True} and {tuple(p) for p in row['cells']}=={tuple(p) for state in row['states'] for p in state['cells']},'Missing exact both-state door operating union')
                    for offset in {tuple(p) for p in row['cells']}|self.operation_offsets(pose,spec)|{(0,0,0)}:
                        self._door_offsets.setdefault(offset,set()).add(row['key'])
            self._door_hits={}
        key=(dim,*at)
        if key not in self._door_hits:
            hits=[]
            for offset,poses in self._door_offsets.items():
                root=[at[i]-offset[i] for i in range(3)]
                for obj in n.row_dict(db,'SELECT * FROM objects INDEXED BY object_root WHERE dim=? AND x=? AND z=? AND y=?',(dim,root[0],root[2],root[1])):
                    if obj['pose'] in poses and not db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?',(dim,obj['uuid'])).fetchone():hits.append(obj)
            self._door_hits[key]=hits
        return self._door_hits[key]

    def resource(self,path,wanted=None):
        m=self.m;path=m.checked(path);key=str(path)
        if key not in self.resources:self.resources[key]={'path':key,'sha256':m.sha(path)}
        row=self.resources[key];m.require(wanted is None or row['sha256']==wanted,'Source occupancy resource binding changed: '+key)
        return row

    def source_point(self,dim,at):
        m,db=self.m,self.db;token=(dim,at[0]>>4,at[2]>>4)
        if token not in self.chunks:
            saved=db.execute('SELECT region_path,region_hash,compression,payload FROM old.chunk_cache WHERE dim=? AND x=? AND z=?',token).fetchone()
            m.require(saved is not None,'UNKNOWN source occupancy: immutable cached chunk absent; no world fallback')
            manifest=m.read(m.meta(db,'binding')['copyManifest'])
            m.require(manifest['files'][saved[0]]['sha256']==saved[1],'Source occupancy cache/manifest binding changed')
            m.require(saved[2] in (1,2,3),'Unknown cached source compression')
            nbt=m.decode_nbt(saved[3],compressed={1:'gzip',2:'zlib',3:None}[saved[2]]);root=m.compound(nbt.root)
            sections={m.compound(s)['Y'].value:m.section_blocks(s) for s in root.get('sections',m.Tag(9,[],10)).value}
            bes={tuple(m.compound(t)[k].value for k in ('x','y','z')):m.compound(t) for t in root.get('block_entities',m.Tag(9,[],10)).value}
            proof={'dimension':dim,'chunk':list(token[1:]),'region':saved[0],'regionSha256':saved[1],'cachedPayloadSha256':m.hashlib.sha256(saved[3]).hexdigest()}
            self.chunks[token]=(sections,bes,proof)
            if len(self.chunks)>4:self.chunks.popitem(last=False)
        self.chunks.move_to_end(token);sections,bes,proof=self.chunks[token]
        be=bes.get(tuple(at));state=m.source_state(sections,at)
        return state,be,proof

    def envelope(self,pose,encoded):
        m=self.m
        if pose in self.geometry:return self.geometry[pose]
        known=self.native_tree_envelope(encoded)
        if known:self.geometry[pose]=known;return known
        previous=self.__dict__.get('_saved_envelopes',{}).get(pose)
        if previous:
            reference=previous['resource'];self.resource(reference['path'],reference['sha256'])
            with Path(reference['path']).open('rb') as stream:stream.seek(previous['offset']);line=stream.read(previous['length'])
            m.require(m.hashlib.sha256(line).hexdigest()==previous['rowSha256'],'Committed runtime-envelope source row changed')
            row=m.json.loads(line);data=m.json.loads(encoded)
            m.require(row['key']==pose and all(row['sourcePose'].get(k)==v for k,v in data.items()),'Committed runtime envelope changed exact SourcePose')
            answer={**previous,'cells':[tuple(p) for p in previous['cells']]};self.geometry[pose]=answer;return answer
        path=m.ROOT/'research/geometry-final/source-geometry.jsonl'
        exports=self.exports;close=False
        if exports is None:
            exports=sqlite3.connect('file:'+Path(self.bindings['poseFragments']['path']).as_posix()+'?mode=ro',uri=True);close=True
        try:
            saved=exports.execute("SELECT data FROM metadata WHERE name='sourceGeometrySha256'").fetchone()
            m.require(saved,'Missing source geometry authority for the runtime envelope')
            reference=self.resource(path,m.json.loads(saved[0]))
            provenance=exports.execute('SELECT source FROM poses WHERE key=?',(pose,)).fetchone()
            m.require(provenance,'Unknown exported source pose for retained occupancy')
            with path.open('rb') as stream:
                while True:
                    offset=stream.tell();line=stream.readline()
                    if not line:break
                    if pose.encode() not in line:continue
                    row=m.json.loads(line)
                    if row.get('key')!=pose:continue
                    source=row['sourcePose'];data=m.json.loads(encoded)
                    m.require(all(source.get(key)==value for key,value in data.items()),'Runtime envelope is not the exact installed source pose')
                    offsets=[cell['cell'] for cell in row['cells']]
                    m.require(0<len(offsets)<=4096 and all(len(at)==3 and all(type(v)is int for v in at) for at in offsets),'Unknown/incomplete runtime footprint envelope')
                    answer={'resource':reference,'offset':offset,'length':len(line),'rowSha256':m.hashlib.sha256(line).hexdigest(),
                            'cells':sorted({tuple(at) for at in offsets}),'openable':row.get('openable'),'functionalDoor':row.get('functionalDoor')}
                    self.geometry[pose]=answer;return answer
            raise RuntimeError('UNKNOWN source occupancy: exact runtime envelope not present: '+pose)
        finally:
            if close:exports.close()

    def native_tree_envelope(self,encoded):
        m=self.m
        if '_native_tree_rows' not in self.__dict__:
            path=m.ROOT/'research/native-functional-bases/unknown-native-tree-footprints.json';reference=self.resource(path,NATIVE_TREE_FOOTPRINT_SHA);report=m.read(path)
            m.require(report.get('status')=='PASS' and report.get('sourceJarSha256')==OVERLAP_BASELINE_SHA,'Exact native tree footprint authority changed')
            self._native_tree_rows=report['poses'];self._native_tree_ref=reference
        pose=m.json.loads(encoded)
        matches=[row for row in self._native_tree_rows if all(row['legacyPose'].get(key,default)==pose.get(key,default) for key,default in (('registryId',None),('properties',{}),('shift',[0,0,0]),('mountY',0))) and not pose.get('legacyStairArt')]
        m.require(len(matches)<=1,'Ambiguous exact native footprint authority')
        if not matches:return None
        row=matches[0]
        return {'resource':self._native_tree_ref,'format':'native_footprint_proof','rowKey':row['poseKey'],'rowSha256':m.digest(row),
                'cells':[tuple(at) for at in row['cells']],'openable':False,'functionalDoor':False}

    def authority(self,pose):
        m=self.m;binding=m.meta(self.db,'binding')
        caps=Path(binding['oldPlan']).parents[2]/'tools/ordinary-stage1/source-capabilities.json'
        capref=self.resource(caps,OVERLAP_CAPABILITIES_SHA)
        if 'capabilities' not in self.__dict__:self.capabilities={row['registryId']:row for row in m.read(caps)['states']}
        cap=self.capabilities.get(pose['registryId'],{})
        jar=Path(binding['source']).parent.parent/'game/dreamwalker-bb-materials.1-streaming.7-blocks.2.jar'
        jarref=self.resource(jar,OVERLAP_BASELINE_SHA);entry='bloodborne_dw/composite/'+pose['registryId'].split(':',1)[1]+'.json'
        token='spec:'+pose['registryId']
        if token not in self.__dict__:
            if '_source_jar' not in self.__dict__:self._source_jar=(jarref,m.zipfile.ZipFile(jar))
            archive=self._source_jar;m.require(archive[0]==jarref,'Source JAR reader belongs to another bound resource')
            raw=archive[1].read(entry)
            self.__dict__[token]=(m.json.loads(raw),{'jar':jarref,'entry':entry,'sha256':m.hashlib.sha256(raw).hexdigest()})
        spec,specref=self.__dict__[token]
        return cap,capref,spec,specref

    def native_origins(self,dim,at,state):
        m=self.m;doc,overlays=m.legacy_native_catalog();rid=state['registryId'];index=None
        if rid.startswith('bloodborne_dw:static_architecture_'):index=int(rid.rsplit('_',1)[1])*128+int(state.get('properties',{}).get('part',0))
        elif rid in overlays:index=overlays[rid]
        if index is None:return []
        result=[]
        for fragment in doc['recipes'][index]['fragments']:
            root=[at[i]+fragment['origin'][i] for i in range(3)];uid=m.synthetic_uuid(dim,root,fragment['pose'])
            obj=n.row_dict(self.db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(dim,uid))
            result.append({'uuid':uid,'legacyPose':fragment['pose'],'root':root,'member':fragment.get('member',False),'object':obj})
        return result

    def strict_original(self,dim,at):
        if self.strict is None:
            self.strict=set();head=self.m.meta(self.db,'nativeLocalRetention');seen=set()
            while head:
                self.m.require(head['sha256'] not in seen,'Retention history cycle');seen.add(head['sha256'])
                receipt=bound(self.m,head);preview=bound(self.m,receipt['preview'])
                for case in preview['cases']:
                    for native in [case['retainedNative']]+case.get('retainedAdditionalNative',[]):
                        self.strict.update((native['dimension'],*point) for point in native['mustRemainOriginalCells'])
                    for owner in case['retainedOwners']:
                        modes={tuple(row['position']):row['policy'] for row in owner.get('memberPolicies',[])}
                        self.strict.update((owner['dimension'],*point) for point in owner['runtimeMemberCells'] if modes.get(tuple(point),'original')=='original')
                head=receipt.get('previousRetention')
            if self.m.meta(self.db,'sourceLocalRetentionJournal'):
                for encoded, in self.db.execute('SELECT data FROM source_local_retentions ORDER BY sequence'):
                    event=self.m.json.loads(encoded)
                    self.strict.update((domain['object']['dim'],*point['position']) for domain in event['originalDomains'] for point in domain['domain'])
                    self.strict.update((row['dimension'],*row['position']) for row in event.get('originalNativeCells',[]))
        return (dim,*at) in self.strict

    def owner_domain(self,dim,uid,original_native=False):
        m,db=self.m,self.db;token=(dim,uid)
        if token in self.domains:return self.domains[token]
        obj=n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',token)
        m.require(len(obj)==1,'UNKNOWN protected identity');obj=obj[0]
        saved=db.execute('SELECT data FROM poses WHERE key=?',(obj['pose'],)).fetchone();m.require(saved,'Unknown retained exact SourcePose')
        encoded=saved[0];pose=m.json.loads(encoded);root=[obj[k] for k in ('x','y','z')]
        prior=db.execute('SELECT data,digest FROM source_visual_domains WHERE dim=? AND uuid=?',token).fetchone() if self.journal_tables else None
        if prior:
            document=m.json.loads(prior[0]);m.require(m.digest(document)==prior[1] and self.domain_equal({**document,'bindings':self.bindings},document) and document['object']==obj,'Committed source owner-domain binding changed')
            self.__dict__.setdefault('_saved_envelopes',{})[obj['pose']]=document['runtimeFootprintEnvelope']
        m.require((obj['kind'] in ('static','whole','preserve') or (obj['kind']=='substrate' and (original_native or token in self.local_native_domains()))) and obj['source_status']!='private_payload','Unknown/private retained mechanism')
        for table in ('selected_objects','fragments','whole_cells','activated_native','cleanup_owners','substrate_fragments'):
            hint=' INDEXED BY fragment_owner' if table=='fragments' else ''
            m.require(not db.execute('SELECT 1 FROM '+table+hint+' WHERE dim=? AND uuid=? LIMIT 1',token).fetchone(),'Retained owner has conversion/cleanup work: '+str(token))
        envelope=self.envelope(obj['pose'],encoded);cap,capref,spec,specref=self.authority(pose)
        state,be,chunk=self.source_point(dim,root);mechanism=None
        cl=cap.get('className','');normal='dev.dreamwalker.bloodbornedw.composite.'
        if be:
            m.require(cl in (normal+'ImportedRootBlock',normal+'ImportedCompactBlock',normal+'CompositeRootBlock'),'Unknown retained CompositeRoot mechanism')
            m.require(cl!=normal+'ImportedCompactBlock' or pose.get('properties',{}).get('compact')=='false','Compact non-root branch is not a retained owner')
            m.require(state=={'registryId':pose['registryId'],'properties':pose.get('properties',{})},'Retained original root state/pose mismatch')
            m.require(set(be)<=OVERLAP_NORMAL_BE and be.get('id',m.Tag(8,'')).value=='bloodborne_dw:composite_cell' and 'resident' in be,'Unknown/private root BE')
            resident=m.compound(be['resident']);payload=m.plain_tag(be.get('payload',m.Tag(10,{})))
            m.require(m.owner_uuid(be['resident'])==uid and resident['root'].value==root and resident['id'].value==pose['registryId'],'Retained root resident identity mismatch')
            m.require(set(payload)<=set(('ImportedTypeVersion','SourceShift','MountY')) and payload.get('ImportedTypeVersion')==1,'Unknown/pending retained payload migration')
            m.require(payload.get('SourceShift',[0,0,0])==pose.get('shift',[0,0,0]) and payload.get('MountY',0)==pose.get('mountY',0),'Retained payload/frame mismatch')
            mechanism='owned_composite'
        else:
            matches=[row for row in self.native_origins(dim,root,state) if row['uuid']==uid and row['root']==root and row['member']]
            m.require(len(matches)==1,'Unknown retained original native root/recipe identity')
            mechanism='legacy_static_recipe'
        owned={};members={tuple(root)}|{tuple(root[i]+off[i] for i in range(3)) for off in envelope['cells']}
        for table in ('stored_contributions','members'):
            rows=self.owner_member_rows(dim,uid,table)
            m.require(len(rows)<=4096,'Retained owner member bound exceeded');owned[table]=m.digest(rows)
            members.update(tuple(row[k] for k in ('x','y','z')) for row in rows)
        operation=None
        if spec.get('openable') or spec.get('functionalDoorPhysics') or envelope['openable'] or envelope['functionalDoor']:
            reference=self.bindings['doorFootprintGuard'];m.require(reference,'Unknown door endpoint authority')
            report=bound(m,reference);rows=[row for row in report['poses'] if row['key']==obj['pose']]
            m.require(report.get('status')=='PASS' and report.get('nativeDescriptor') is True and report.get('baselineModJarSha256')==OVERLAP_BASELINE_SHA and len(rows)==1,'Missing exact known both-state operating domain')
            door=rows[0];m.require({row['open'] for row in door['states']}=={False,True} and {tuple(p) for p in door['cells']}=={tuple(p) for row in door['states'] for p in row['cells']},'Incomplete door endpoint union')
            members.update(tuple(root[i]+off[i] for i in range(3)) for off in door['cells']);members.add(tuple(root))
            constraints=self.operation_offsets(pose,spec);members.update(tuple(root[i]+off[i] for i in range(3)) for off in constraints)
            operation={'doorFootprintGuard':reference,'endpointCells':door['cells'],'essentialMask':spec.get('essentialMask',[[0,0,0]]),'support':spec.get('support',{'required':False})}
        else:
            members.update(tuple(root[i]+off[i] for i in range(3)) for off in self.operation_offsets(pose,spec))
        m.require(len(members)<=4096,'Retained source domain is not finite')
        points=[]
        for at in sorted(members):
            args=(dim,*at);actual,fields,cached=self.source_point(dim,at)
            m.require(not db.execute('SELECT 1 FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone(),'REAL_OCCUPANCY: retained runtime/service/door domain has replacement work: '+str(args))
            m.require(not db.execute('SELECT 1 FROM placements WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone(),'Retained source physical/service member acquired placement')
            points.append({'position':list(at),'sourceState':actual,'beDigest':self.source_be_digest(dim,at,fields,cached),'chunk':cached})
        answer={'schemaVersion':1,'bindings':self.bindings,'object':obj,'poseDataSha256':m.hashlib.sha256(encoded.encode()).hexdigest(),'root':root,
                'mechanism':mechanism,'runtimeFootprintEnvelope':{**envelope,'cells':[list(p) for p in envelope['cells']]},'capability':capref,'sourceSpec':specref,
                'savedMembershipDigests':owned,'rootState':state,'rootBeDigest':self.source_be_digest(dim,root,be,chunk),'domain':points,'operationGuard':operation}
        self.domains[token]=answer
        if len(self.domains)>512:self.domains.popitem(last=False)
        return answer

    def be_digest(self,fields):
        if fields is None:return None
        m=self.m
        return m.hashlib.sha256(m.encode_nbt(m.NbtFile('',m.Tag(10,fields)))).hexdigest()

    def source_be_digest(self,dim,at,fields,proof):
        """Memoize only the actual immutable source chunk's decoded BE object."""
        entry=self.chunks.get((dim,at[0]>>4,at[2]>>4))
        if fields is None or entry is None or entry[1].get(tuple(at)) is not fields or proof!=entry[2]:return self.be_digest(fields)
        payload=entry[2].get('cachedPayloadSha256')
        if not isinstance(payload,str):return self.be_digest(fields)
        key=(payload,dim,*at);memo=self.__dict__.setdefault('_source_be_digests',self.m.collections.OrderedDict())
        if key not in memo:
            memo[key]=self.be_digest(fields)
            if len(memo)>4096:memo.popitem(last=False)
        memo.move_to_end(key);return memo[key]

    def source_native_members(self,dim,obj,pose):
        """Actual cached helper data, not a claim about a runtime footprint."""
        m=self.m;path=m.PROJECT/'src/architecture/resources/bloodborne_dw/static-architecture-v1274.json';reference=self.resource(path,ORIGINAL_NATIVE_CATALOG_SHA)
        doc,_=m.legacy_native_catalog()
        def signature(value):return (value['registryId'],tuple(sorted(value.get('properties',{}).items())),tuple(value.get('shift',[0,0,0])),value.get('mountY',0),value.get('legacyStairArt',''))
        if '_native_member_candidates' not in self.__dict__:
            poses={};offsets={}
            for row in doc['poses']:poses.setdefault(signature(row),[]).append(row['key'])
            for recipe in doc['recipes']:
                for fragment in recipe['fragments']:
                    if fragment.get('member'):offsets.setdefault(fragment['pose'],set()).add(tuple(-value for value in fragment['origin']))
            self._native_member_candidates=(poses,offsets)
        poses,offsets=self._native_member_candidates;root=tuple(obj[k] for k in ('x','y','z'));uid=obj['uuid']
        keys=[key for key in poses.get(signature(pose),[]) if m.synthetic_uuid(dim,root,key)==uid];m.require(len(keys)<=1,'Ambiguous original native source identity')
        found=set()
        for key in keys:
            candidates=offsets.get(key,set());m.require(len(candidates)<=4096,'Original native source helper candidate bound exceeded')
            for offset in candidates:
                at=tuple(root[i]+offset[i] for i in range(3));state,_,_=self.source_point(dim,at)
                if any(row['uuid']==uid and row['legacyPose']==key and tuple(row['root'])==root and row['member'] for row in self.native_origins(dim,at,state)):found.add(at)
        return found,{'resource':reference,'canonicalPoseKeys':keys,'actualCachedMemberCells':[list(at) for at in sorted(found)]}

    def original_members(self,dim,uid):
        """Preserve original source data without needing its conversion class."""
        m,db=self.m,self.db;token=(dim,uid)
        memo=self.__dict__.setdefault('_original_member_scopes',{})
        if token in memo:return memo[token]
        rows=n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',token);m.require(len(rows)==1,'Unknown directly retained source object');obj=rows[0]
        encoded=db.execute('SELECT data FROM poses WHERE key=?',(obj['pose'],)).fetchone();m.require(encoded,'Original source pose missing');pose=m.json.loads(encoded[0]);root=tuple(obj[k] for k in ('x','y','z'))
        try:envelope=self.envelope(obj['pose'],encoded[0])
        except RuntimeError as error:
            if not (str(error).startswith('UNKNOWN source occupancy: exact runtime envelope not present:') or str(error)=='Unknown exported source pose for retained occupancy'):raise
            envelope=None
        members={root};owned={};native_proof=None
        if envelope:members.update(tuple(root[i]+offset[i] for i in range(3)) for offset in envelope['cells'])
        for table in ('stored_contributions','members'):
            saved=self.owner_member_rows(dim,uid,table);m.require(len(saved)<=4096,'Original saved member bound exceeded');owned[table]=m.digest(saved);members.update(tuple(row[k] for k in ('x','y','z')) for row in saved)
        actual,native_proof=self.source_native_members(dim,obj,pose);members.update(actual)
        operation=None;report_ref=self.bindings['doorFootprintGuard']
        if report_ref:
            report=bound(m,report_ref);matches=[row for row in report['poses'] if row['key']==obj['pose']]
            if matches:
                m.require(len(matches)==1 and report.get('status')=='PASS' and report.get('nativeDescriptor') is True and report.get('baselineModJarSha256')==OVERLAP_BASELINE_SHA,'Original door source authority changed')
                row=matches[0];m.require({state['open'] for state in row['states']}=={False,True} and {tuple(at) for at in row['cells']}=={tuple(at) for state in row['states'] for at in state['cells']},'Original door endpoint union changed');members.update(tuple(root[i]+offset[i] for i in range(3)) for offset in row['cells']);operation={'doorFootprintGuard':report_ref,'endpointCells':row['cells']}
        # Optional source constraints add protection; an unrecognised storage
        # class or private payload never requires a conversion implementation.
        try:_,_,spec,specref=self.authority(pose)
        except KeyError as error:
            if 'There is no item named' not in str(error):raise
            spec=None;specref=None
        if spec:members.update(tuple(root[i]+offset[i] for i in range(3)) for offset in self.operation_offsets(pose,spec))
        m.require(0<len(members)<=4096,'Original known source members exceed finite bound')
        answer=(obj,members,{'poseDataSha256':m.hashlib.sha256(encoded[0].encode()).hexdigest(),'runtimeFootprintEnvelope':({**envelope,'cells':[list(at) for at in envelope['cells']]} if envelope else None),'savedMembershipDigests':owned,'actualNativeSourceMembers':native_proof,'operationGuard':operation,'sourceSpec':specref})
        memo[token]=answer;return answer

    def original_domain(self,dim,uid,unreplaced=True):
        m,db=self.m,self.db;obj,members,authority=self.original_members(dim,uid);token=(dim,uid)
        for table in ('selected_objects','fragments','whole_cells','activated_native','cleanup_owners','substrate_fragments'):
            hint=' INDEXED BY fragment_owner' if table=='fragments' else ''
            if unreplaced:m.require(not db.execute('SELECT 1 FROM '+table+hint+' WHERE dim=? AND uuid=? LIMIT 1',token).fetchone(),'Original retained source has conversion/cleanup work: '+table)
        points=[]
        for at in sorted(members):
            if unreplaced:
                for table in ('cell_queue','placements'):m.require(not db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone(),'Original retained source member has replacement work')
            state,be,chunk=self.source_point(dim,at);points.append({'position':list(at),'sourceState':state,'beDigest':self.source_be_digest(dim,at,be,chunk),'chunk':chunk})
        state,be,root_chunk=self.source_point(dim,tuple(obj[k] for k in ('x','y','z')))
        native=any(row['uuid']==uid and row['member'] for row in self.native_origins(dim,tuple(obj[k] for k in ('x','y','z')),state))
        return {'schemaVersion':1,'bindings':self.bindings,'object':obj,'root':[obj[k] for k in ('x','y','z')],'mechanism':'source_original_snapshot',**authority,'rootState':state,'rootBeDigest':self.source_be_digest(dim,tuple(obj[k] for k in ('x','y','z')),be,root_chunk),'nativeRootIdentity':native,'domain':points}

    def contact(self,dim,at,row,domain):
        m,db=self.m,self.db;uid=row['uuid'];args=(dim,*at)
        m.require(row['reason'] in ('functional_guest','whole_local_overlap'),'REAL_OCCUPANCY/UNKNOWN protection class: '+row['reason'])
        m.require(not self.strict_original(dim,at),'Historical strict original member cannot become visual-only')
        m.require(list(at) not in [point['position'] for point in domain['domain']],'UNKNOWN_OR_REAL_DOMAIN: point is inside conservative runtime envelope/service/door domain; exact compatibility/local fallback required')
        m.require(not self.door_operations_at(dim,at),'REAL_OCCUPANCY: another retained door endpoint/essential/support domain')
        state,be,chunk=self.source_point(dim,at)
        classification=self.classification()
        m.require(state['registryId'] not in classification.get('protectedRegistryIds',[]),'REAL_OCCUPANCY: actual protected v12 state')
        m.require(be is None,'REAL_OCCUPANCY: source target has BlockEntity data')
        for table in ('stored_contributions','members'):
            refs=n.row_dict(db,'SELECT * FROM old.'+table+' WHERE dim=? AND x=? AND y=? AND z=?',args)
            for ref in refs:
                selected=db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?',(dim,ref['uuid'])).fetchone()
                owner=db.execute('SELECT kind FROM objects WHERE dim=? AND uuid=?',(dim,ref['uuid'])).fetchone()
                m.require(ref['uuid']!=uid and (selected or owner==('substrate',)),'REAL_OCCUPANCY: retained/unknown saved source member')
        roots=self.native_origins(dim,at,state)
        for origin in roots:
            m.require(origin['uuid']!=uid and len(origin['object'])==1 and origin['object'][0]['kind']=='substrate','REAL_OCCUPANCY/UNKNOWN: retained native recipe member at target')
        m.require(not db.execute('SELECT 1 FROM objects WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',(*args,uid)).fetchone(),'Retained source root cannot be visual-only')
        return {'decision':'VISUAL_RESERVATION_ONLY','protectedRow':row,'allProtectedRows':n.row_dict(db,'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? ORDER BY uuid',args),
                'sourceState':state,'sourceBE':None,'sourceChunk':chunk,'nativeOrigins':roots,
                'targetOutsideRuntimeEnvelopeAndDoorDomain':True,'originalCollisionTransferUnchanged':True}

    def clearance(self,dim,at,incoming):
        m,db=self.m,self.db;args=(dim,*at)
        if (dim,*(value//16*16 for value in at)) in self.original_zones():
            raise RuntimeError('Original source section requires local preservation\nProtected source contact requires a direct compatible/local fallback: '+m.canonical({'dimension':dim,'position':list(at),'incoming':incoming,'originalZone':True}))
        rows=n.row_dict(db,'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?',args)
        for row in rows:
            try:
                # A proved existing CompositeBE carrier remains the existing
                # compatible route; a future door-domain row is never exempted.
                if row['reason'] not in ('openable_both_states_local_helpers',LOCAL_ORIGINAL_REASON) and not self.strict_original(dim,at):
                    bridge=db.execute('SELECT host_state,retained_resident FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone()
                    if bridge:
                        state,be,_=self.source_point(dim,at);classification=self.classification()
                        detail=m.functional_be_conflict(db,dim,be) if be else None
                        if state['registryId'] not in classification.get('protectedRegistryIds',[]) and be and m.bridge_candidate(db,dim,be,detail):continue
                domain=self.owner_domain(dim,row['uuid']);proof=self.contact(dim,at,row,domain)
            except (RuntimeError,KeyError,ValueError,TypeError) as error:
                details=m.canonical({'dimension':dim,'position':list(at),'incoming':incoming,'protected':row,'reason':str(error)})
                if incoming.get('kind')=='legacy_native':message='Required native visual overhang meets a protected local object; retain that particular source root: '+str((dim,incoming['uuid'],tuple(at)))
                else:message='Wide native visual transfer meets a protected local object: '+str((dim,tuple(at),incoming))
                raise RuntimeError(message+'\nProtected source contact requires a direct compatible/local fallback: '+details) from error
            if self.files is None:self.files=source_files(m)
            data={**proof,'dimension':dim,'position':list(at),'incoming':incoming,'sourceFiles':self.files,'domainDigest':m.digest(domain)}
            encoded=m.canonical(domain);saved=db.execute('SELECT data,digest FROM source_visual_domains WHERE dim=? AND uuid=?',(dim,row['uuid'])).fetchone()
            m.require(saved is None or saved==(encoded,m.digest(domain)),'Retained source domain changed after a committed visual-only contact')
            db.execute('INSERT OR IGNORE INTO source_visual_domains VALUES(?,?,?,?)',(dim,row['uuid'],encoded,m.digest(domain)))
            key=(*args,row['uuid'],m.canonical(incoming));encoded=m.canonical(data);digest=m.digest(data)
            saved=db.execute('SELECT data,digest FROM source_visual_contacts WHERE dim=? AND x=? AND y=? AND z=? AND uuid=? AND incoming=?',key).fetchone()
            m.require(saved is None or saved==(encoded,digest),'A source contact changed across its atomic resume boundary')
            changed=db.execute('INSERT OR IGNORE INTO source_visual_contacts VALUES(?,?,?,?,?,?,?,?)',(*key,encoded,digest)).rowcount
            if changed:
                previous=m.meta(db,'sourceVisualReservationJournal') or {'schemaVersion':1,'contacts':0,'head':None,'sourceBinding':self.bindings['binding']}
                m.require(previous['sourceBinding']==self.bindings['binding'],'Visual reservation journal belongs to another source')
                m.meta(db,'sourceVisualReservationJournal',{**previous,'contacts':previous['contacts']+1,'head':m.digest({'previous':previous['head'],'contact':digest})})


def validate_source_visual_reservations(m,db,finalized=False,source_files_validator=None):
    """Only accepted encounter rows, no city/owner census and no map reads."""
    journal=m.meta(db,'sourceVisualReservationJournal')
    if not journal:return
    check_sources=source_files_validator or (lambda files:require_source_files(m,db,files))
    context=SourceVisualReservations(m,db);domains={};count=0;head=None;source_tuples=set();originals_validated=False
    for dim,x,y,z,uid,incoming,encoded,digest in db.execute('SELECT * FROM source_visual_contacts ORDER BY rowid'):
        data=m.json.loads(encoded);m.require(m.digest(data)==digest,'Committed source visual contact changed');source_tuple=m.canonical(data['sourceFiles'])
        if source_tuple not in source_tuples:check_sources(data['sourceFiles']);source_tuples.add(source_tuple)
        token=(dim,uid)
        if token not in domains:
            saved=db.execute('SELECT data,digest FROM source_visual_domains WHERE dim=? AND uuid=?',token).fetchone();m.require(saved,'Missing retained source domain journal')
            domain=m.json.loads(saved[0]);m.require(m.digest(domain)==saved[1] and context.domain_equal(context.owner_domain(dim,uid),domain),'Original retained runtime/root/payload/ledger domain changed')
            domains[token]=domain
        domain=domains[token];m.require(data['domainDigest']==m.digest(domain),'Visual contact references another retained source domain')
        row=data['protectedRow'];m.require(row in n.row_dict(db,'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?',(dim,x,y,z)),'Original visual protection provenance changed')
        actor=data['incoming'];original_actor=False
        if actor.get('kind')=='legacy_native':
            retained=context.local_native_domains().get((dim,actor['uuid']))
            original_actor=bool(retained and retained['object']['pose']==actor['pose'] and retained['root']==actor['root'])
        elif actor.get('kind')=='wide_native':
            retained=context.local_native_cells().get((dim,*actor['root']));original_actor=bool(retained and retained['sourceState']==actor['state'])
        superseded=(dim,x//16*16,y//16*16,z//16*16) in context.original_zones() or original_actor
        if superseded:
            if not originals_validated:validate_source_local_retentions(m,db,finalized,source_files_validator=check_sources);originals_validated=True
            state,be,chunk=context.source_point(dim,(x,y,z));current_rows=n.row_dict(db,'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? ORDER BY uuid',(dim,x,y,z))
            m.require(state==data['sourceState'] and be is None and data['sourceBE'] is None and chunk==data['sourceChunk'] and context.native_origins(dim,(x,y,z),state)==data['nativeOrigins'] and all(saved in current_rows for saved in data['allProtectedRows']),'Superseded positive contact lost its immutable original source/protection data')
        else:
            current=context.contact(dim,[x,y,z],row,domain)
            m.require(all(data[key]==value for key,value in current.items()),'Source visual-only contact/door occupancy proof changed')
        if finalized and not superseded:
            actual=db.execute('SELECT source_state,composition FROM placements WHERE dim=? AND x=? AND y=? AND z=?',(dim,x,y,z)).fetchone()
            m.require(actual and m.json.loads(actual[0])==data['sourceState'] and not actual[1].startswith('whole:'),'Accepted visual contact lacks its ordinary exact source-state placement')
        count+=1;head=m.digest({'previous':head,'contact':digest})
    m.require(journal=={'schemaVersion':1,'contacts':count,'head':head,'sourceBinding':m.meta(db,'binding')},'Visual-only journal is incomplete or changed')
    return originals_validated


def attach_source_journal_original(m,db):
    """Source admission uses an ordinary handle, not connect's schema writer."""
    if not any(m.meta(db,key) for key in ('sourceVisualReservationJournal','sourceLocalRetentionJournal')):return
    path=m.checked(Path(m.meta(db,'binding')['oldPlan'])/'plan.sqlite')
    attached=[row[2] for row in db.execute('PRAGMA database_list') if row[1]=='old']
    if attached:m.require(len(attached)==1 and m.checked(attached[0])==path,'Source journal attached to another immutable discovery')
    else:db.execute('ATTACH DATABASE ? AS old',('file:'+path.as_posix()+'?mode=ro',))


def check_retained_native_member(context,dim,at,state):
    """Real retained members precede the complete-native visual shortcut."""
    m,db=context.m,context.db
    if (dim,*(value//16*16 for value in at)) in context.original_zones():
        raise RuntimeError('Original source section requires local preservation\nProtected source contact requires a direct compatible/local fallback: '+m.canonical({'dimension':dim,'position':list(at),'incoming':{'kind':'wide_native','root':list(at),'state':state},'originalZone':True}))
    if (dim,*at) in context.local_native_cells():
        raise RuntimeError('Retained original native cell needs a direct local fallback\nProtected source contact requires a direct compatible/local fallback: '+m.canonical({'dimension':dim,'position':list(at),'incoming':{'kind':'wide_native','root':list(at),'state':state},'retainedCell':True}))
    for origin in context.native_origins(dim,at,state):
        rows=origin['object'];m.require(len(rows)==1,'Unknown original native recipe identity')
        obj=rows[0]
        local=context.local_native_domains().get((dim,obj['uuid']))
        if obj['kind']=='substrate' and local is None:continue
        if obj['kind']=='whole' and db.execute("SELECT 1 FROM selected_objects WHERE dim=? AND uuid=? AND kind='whole'",(dim,obj['uuid'])).fetchone():continue
        # A raw recipe fragment can also be visual-only. Only a bound complete
        # runtime footprint may establish the retained root/helper domain.
        encoded=db.execute('SELECT data FROM poses WHERE key=?',(obj['pose'],)).fetchone()
        _,members,_=context.original_members(dim,obj['uuid']) if local is None else (obj,{tuple(row['position']) for row in local['domain']},None)
        if local is not None and origin['member']:members.add(tuple(at))
        if tuple(at) in members:
            raise RuntimeError('A consumed native helper belongs to a retained/protected local object: '+str((dim,obj['uuid'],obj['kind'],obj['reason'])))


def local_original_scope(context,dim,uid,native=False):
    """A single known owner; never discover owners through another footprint."""
    m=context.m;obj,points,_=context.original_members(dim,uid)
    if native:m.require(obj['kind'] in ('static','preserve','whole') or (obj['kind']=='substrate' and (dim,uid) in context.local_native_domains()),'Retained native needs its confirmed original runtime domain')
    return obj,points


def pending_native_roots(context,dim,at,state,native):
    """Filter already-original visual references before the old kind guard."""
    m,db=context.m,context.db;retained=context.local_native_domains()
    if not retained:return m.native_roots_at(db,dim,at,state,native)
    if native and native.get('nativeVisualComplete',True):return []
    origins=context.native_origins(dim,at,state)
    if not origins:return m.native_roots_at(db,dim,at,state,native)
    result=[]
    for origin in origins:
        if (dim,origin['uuid']) in retained:continue
        m.require(len(origin['object'])==1,'Unknown remaining native source identity');obj=origin['object'][0]
        if obj['kind']=='whole' and db.execute("SELECT 1 FROM selected_objects WHERE dim=? AND uuid=? AND kind='whole'",(dim,obj['uuid'])).fetchone():continue
        m.require(obj['kind']=='substrate','A consumed native helper belongs to a retained/protected local object: '+str((dim,obj['uuid'],obj['kind'],obj['reason'])))
        result.append(tuple(obj[key] for key in ('uuid','pose','x','y','z')))
    return result


def check_whole_native_contact(context,dim,at,incoming):
    roles=n.row_dict(context.db,'SELECT * FROM whole_cells WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at))
    if roles:
        message='Native overhang would overlap a dedicated whole-object role' if incoming['kind']=='legacy_native' else 'Wide native transfer meets a dedicated statue role'
        raise RuntimeError(message+'\nProtected source contact requires a direct compatible/local fallback: '+context.m.canonical({'dimension':dim,'position':list(at),'incoming':incoming,'wholeRows':roles}))


def original_native_cell(context,dim,at,state=None,unreplaced=False):
    m,db=context.m,context.db;actual,be,chunk=context.source_point(dim,at)
    m.require(state is None or actual==state,'Original native-cell source state changed')
    uid='original_native_cell:'+m.digest({'dimension':dim,'position':list(at),'state':actual})
    if unreplaced:
        for table in ('cell_queue','placements'):m.require(not db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone(),'Original native cell has replacement work')
    return {'dimension':dim,'position':list(at),'sourceState':actual,'sourceBeDigest':context.be_digest(be),'sourceChunk':chunk,'uuid':uid}


def unchanged_protected_contact(context,dim,at,protected,unreplaced=False):
    """Keep foreign data opaque: it is not selected, rewritten or cleaned up."""
    m,db=context.m,context.db;uid=protected['uuid'];args=(dim,*at)
    m.require((protected['dim'],protected['x'],protected['y'],protected['z'])==args,'Protected contact coordinates changed')
    obj=n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(dim,uid));encoded=None
    if not obj:
        cell=context.local_native_cells().get(args);m.require(cell and cell['uuid']==uid and original_native_cell(context,dim,at)==cell,'Unknown original native-cell contact')
        obj=[{'dim':dim,'uuid':uid,'kind':'native_cell','x':at[0],'y':at[1],'z':at[2]}]
    else:
        m.require(len(obj)==1,'Unknown already-retained protected identity')
        encoded=db.execute('SELECT data FROM poses WHERE key=?',(obj[0]['pose'],)).fetchone();m.require(encoded,'Protected original SourcePose disappeared')
    for table in ('selected_objects','fragments','whole_cells','activated_native','cleanup_owners','substrate_fragments'):
        hint=' INDEXED BY fragment_owner' if table=='fragments' else ''
        m.require(not db.execute('SELECT 1 FROM '+table+hint+' WHERE dim=? AND uuid=? LIMIT 1',(dim,uid)).fetchone(),'Foreign protected owner must remain excluded from conversion/cleanup: '+table)
    rows=n.row_dict(db,'SELECT * FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? ORDER BY uuid',args);m.require(protected in rows,'Original protected contact provenance disappeared')
    state,be,chunk=context.source_point(dim,at)
    if unreplaced:
        for table in ('cell_queue','placements'):m.require(not db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone(),'Foreign protected contact still has replacement work')
    return {'dimension':dim,'position':list(at),'object':obj[0],'poseDataSha256':m.hashlib.sha256(encoded[0].encode()).hexdigest() if encoded else None,'protectedRow':protected,'allProtectedRows':rows,'sourceState':state,'sourceBeDigest':context.be_digest(be),'sourceChunk':chunk}


def automatic_source_zone_retention(m,db,context,dim,at,state,error,finalization_contact=None):
    """Keep one source section and complete objects crossing its real parts."""
    marker='\nProtected source contact requires a direct compatible/local fallback: '
    incoming=None;contact=tuple(at)
    if marker in str(error):
        detail=m.json.loads(str(error).split(marker,1)[1]);m.require(detail['dimension']==dim,'Original zone contact changed dimension');contact=tuple(detail['position']);incoming=detail.get('incoming')
    elif not str(error).startswith('A consumed native helper belongs to a retained/protected local object: '):return False
    minimum=tuple(value//16*16 for value in contact);maximum=tuple(value+15 for value in minimum)
    cells={(x,y,z) for x in range(minimum[0],minimum[0]+16) for y in range(minimum[1],minimum[1]+16) for z in range(minimum[2],minimum[2]+16)}
    if finalization_contact is None:
        m.require(not db.in_transaction,'Original section fallback requires caller batch rollback');db.execute('BEGIN IMMEDIATE')
    else:m.require(db.in_transaction,'Completed source-domain retention requires its caller atomic boundary')
    try:
        m.require(m.meta(db,'nativeComposeCursor')['phase']=='NATIVE_TRANSFER' and not m.meta(db,'applyStarted') and not m.meta(db,'compositionGeneration') and not db.execute('SELECT 1 FROM placements LIMIT 1').fetchone(),'Original section fallback requires unfinished native composition')
        if finalization_contact is None:
            m.require(db.execute('SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone()==(0,),'Original section must retry the actual committed pending work')
        else:
            m.require(not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone() and db.execute('SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone()==(1,),'Completed source-domain retention cannot bypass pending native work')
            m.require(finalization_contact['dimension']==dim and finalization_contact['position']==list(at) and contact==tuple(at),'Completed source-domain contact changed')
            saved=db.execute('SELECT data,digest FROM source_visual_domains WHERE dim=? AND uuid=?',(dim,finalization_contact['uuid'])).fetchone()
            m.require(saved and saved[1]==finalization_contact['domainDigest'],'Completed source-domain authority disappeared')
            domain=m.json.loads(saved[0]);m.require(m.digest(domain)==saved[1] and domain['bindings']==context.bindings and domain['object']['dim']==dim and domain['object']['uuid']==finalization_contact['uuid'],'Completed source-domain source/owner binding changed')
            expected=[row for row in domain['domain'] if row['position']==list(at)];m.require(len(expected)==1,'Completed source contact is outside the saved original domain')
            actual,be,proof=context.source_point(dim,at)
            m.require({'sourceState':actual,'beDigest':context.source_be_digest(dim,at,be,proof),'chunk':proof}=={key:expected[0][key] for key in ('sourceState','beDigest','chunk')} and n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(dim,finalization_contact['uuid']))==[domain['object']],'Completed original source point/object data changed')
        actual,_,_=context.source_point(dim,at);m.require(actual==state,'Original section source seed changed')
        zone=context.zone_snapshot(dim,minimum);cursor=m.meta(db,'nativeComposeCursor');retention=m.meta(db,'nativeLocalRetention');head=m.meta(db,'sourceLocalRetentionJournal');before=n.counts(db)
        uids=set();wide=set();objects={};wide_records={};native_cells={};union=set(cells);sources={}
        def budget():
            m.require(len(uids)+len(wide)<=4096 and len(union)<=32768,'Original section complete-object budget exceeded')
        def known(uid):
            rows=n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(dim,uid))
            m.require(len(rows)<=1,'Ambiguous source owner')
            if rows:uids.add(uid)
        def discover(points,section=False):
            for cell in points:
                saved=point(m,db,dim,cell);m.require(not saved['placements'],'Cannot retain an already placed source section')
                uids.update(row['uuid'] for row in saved['fragments']+saved['whole_cells']+saved['substrate_fragments'])
                wide.update((row['rx'],row['ry'],row['rz']) for row in saved['native_visual_fragments']);wide.update((row['x'],row['y'],row['z']) for row in saved['native_visual_roots'])
                if section:
                    for row in saved['protected_cells']:known(row['uuid'])
                for table in ('stored_contributions','members'):
                    for row in n.row_dict(db,'SELECT uuid FROM old.'+table+' WHERE dim=? AND x=? AND y=? AND z=?',(dim,*cell)):known(row['uuid'])
                original,be,_=context.source_point(dim,cell)
                for origin in context.native_origins(dim,cell,original):
                    if section or origin['member']:
                        for obj in origin['object']:uids.add(obj['uuid'])
                for obj in n.row_dict(db,'SELECT * FROM objects INDEXED BY object_root WHERE dim=? AND x=? AND z=? AND y=?',(dim,cell[0],cell[2],cell[1])):uids.add(obj['uuid'])
                if be:
                    # Recognised owner headers locate saved parts; private
                    # payload bytes remain opaque and are never converted.
                    try:
                        owners=list(m.be_owners(be))+([be['resident']] if 'resident' in be else [])
                        for owner in owners:known(m.owner_uuid(owner))
                    except (KeyError,TypeError,ValueError,m.NbtError):pass
                budget()
        candidates=db.execute(
            'SELECT DISTINCT o.uuid FROM pose_cells AS p CROSS JOIN objects AS o INDEXED BY object_pose_zone '
            'WHERE o.pose=p.pose AND o.dim=? AND o.x BETWEEN ?-p.dx AND ?-p.dx '
            'AND o.z BETWEEN ?-p.dz AND ?-p.dz AND o.y BETWEEN ?-p.dy AND ?-p.dy LIMIT 4097',
            (dim,minimum[0],maximum[0],minimum[2],maximum[2],minimum[1],maximum[1]))
        uids.update(row[0] for row in candidates);budget()
        discover(cells,section=True)
        if finalization_contact is not None:known(finalization_contact['uuid'])
        if incoming and incoming.get('kind')=='legacy_native':uids.add(incoming['uuid'])
        elif incoming and incoming.get('kind')=='wide_native' and tuple(incoming['root']) not in cells:wide.add(tuple(incoming['root']))
        budget()
        exports=context.exports;close=exports is None
        if close:exports=sqlite3.connect('file:'+Path(context.bindings['poseFragments']['path']).as_posix()+'?mode=ro',uri=True)
        try:
            while uids-set(objects) or wide-set(wide_records):
                budget()
                for uid in sorted(uids-set(objects)):
                    obj,members,_=context.original_members(dim,uid);source=context.original_domain(dim,uid,unreplaced=False)
                    selected=n.row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid))
                    fragments=n.row_dict(db,'SELECT * FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',(dim,uid))
                    roles=n.row_dict(db,'SELECT * FROM whole_cells WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',(dim,uid))
                    activated=n.row_dict(db,'SELECT * FROM activated_native WHERE dim=? AND uuid=?',(dim,uid))
                    substrate=n.row_dict(db,'SELECT * FROM substrate_fragments WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',(dim,uid))
                    cleanup=n.row_dict(db,'SELECT * FROM cleanup_owners WHERE dim=? AND uuid=?',(dim,uid))
                    m.require(len(selected)<=1 and len(activated)<=1 and len(cleanup)<=1 and max(len(fragments),len(roles),len(substrate))<=4096,'Original object work is not complete and bounded')
                    m.require(not selected or all(selected[0][key]==obj[key] for key in ('dim','uuid','pose','x','y','z','kind')),'Original selected identity changed')
                    m.require(not (fragments or roles) or selected,'Original owner fragments lack their exact selected identity')
                    m.require(all(row['pose']==obj['pose'] for row in fragments+substrate),'Original owner work has another pose')
                    if activated:
                        expected={(obj['x']+dx,obj['y']+dy,obj['z']+dz,dx,dy,dz) for dx,dy,dz in db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=?',(obj['pose'],))}
                        m.require(0<len(expected)<=4096 and expected=={(row['x'],row['y'],row['z'],row['dx'],row['dy'],row['dz']) for row in substrate},'Active native original requires complete exact derived work')
                    else:m.require(not substrate,'Native derived work lost its activation')
                    objects[uid]={'object':obj,'selected':selected,'fragments':fragments,'whole_cells':roles,'activated_native':activated,'substrate_fragments':substrate,'cleanup_owners':cleanup}
                    sources[uid]=source;union.update(members);union.update((row['x'],row['y'],row['z']) for row in fragments+roles+substrate);budget()
                    discover(members)
                for root in sorted(wide-set(wide_records)):
                    original,_,_=context.source_point(dim,root);key=m.canonical(original)
                    exported=exports.execute('SELECT data FROM native WHERE state=?',(key,)).fetchone()
                    m.require(exported and m.json.loads(exported[0]).get('nativeVisualComplete',True),'Original wide source needs its bound complete native model')
                    offsets=exports.execute('SELECT x,y,z FROM native_fragments WHERE state=? LIMIT 4097',(key,)).fetchall();m.require(0<len(offsets)<=4096,'Original wide source fragments are not complete/bounded')
                    expected={(*tuple(root[i]+offset[i] for i in range(3)),*offset) for offset in offsets}
                    roots=n.row_dict(db,'SELECT * FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?',(dim,*root))
                    rows=n.row_dict(db,'SELECT * FROM native_visual_fragments WHERE dim=? AND rx=? AND ry=? AND rz=? ORDER BY x,y,z LIMIT 4097',(dim,*root))
                    pending=incoming and incoming.get('kind')=='wide_native' and tuple(incoming['root'])==root
                    m.require((roots==[{'dim':dim,'x':root[0],'y':root[1],'z':root[2],'state':key}] and expected=={(row['x'],row['y'],row['z'],row['dx'],row['dy'],row['dz']) for row in rows} and all(row['state']==key for row in rows)) or (pending and not roots and not rows),'Original wide native requires its exact complete committed rows or zero pending rows')
                    native_cells[root]=original_native_cell(context,dim,root,original)
                    wide_records[root]={'rootRows':roots,'fragments':rows,'sourceState':original,'exportRowSha256':m.hashlib.sha256(exported[0].encode()).hexdigest()}
                    union.add(root);union.update(row[:3] for row in expected);budget();discover({root})
        finally:
            if close:exports.close()
        strict=set(cells);strict.update(tuple(row['position']) for source in sources.values() for row in source['domain']);strict.update(native_cells)
        plan=[]
        for cell in sorted(union):
            saved=point(m,db,dim,cell);m.require(not saved['placements'],'Original section encountered finalized work')
            remaining={'fragments':[row for row in saved['fragments'] if row['uuid'] not in uids],
                       'whole_cells':[row for row in saved['whole_cells'] if row['uuid'] not in uids],
                       'substrate_fragments':[row for row in saved['substrate_fragments'] if row['uuid'] not in uids],
                       'native_visual_fragments':[row for row in saved['native_visual_fragments'] if (row['rx'],row['ry'],row['rz']) not in wide],
                       'native_visual_roots':[row for row in saved['native_visual_roots'] if (row['x'],row['y'],row['z']) not in wide]}
            original,_,_=context.source_point(dim,cell);foreign=[]
            for origin in context.native_origins(dim,cell,original):
                if origin['uuid'] not in uids:foreign.extend(n.row_dict(db,'SELECT * FROM activated_native WHERE dim=? AND uuid=?',(dim,origin['uuid'])))
            remaining['foreignActivatedNative']=foreign;needed=any(remaining.values())
            m.require(cell not in strict or not needed,'Unsupported work remains on an original section/object member: '+str((dim,cell,remaining)))
            m.require(saved['queue'] in ([],[{'processed':0}],[{'processed':1}]) and (not needed or saved['queue']),'Original section has inconsistent queue work')
            if saved['queue'] or any(saved[key] for key in ('fragments','whole_cells','substrate_fragments','native_visual_fragments','native_visual_roots','bridge_cells')):
                plan.append({'position':list(cell),'before':saved,'remainingDemands':remaining,'retire':bool(saved['queue']) and not needed})
        # All source snapshots and complete derived rows are bound above before
        # the first mutation; any failure rolls this entire fallback back.
        for uid,record in sorted(objects.items()):
            for table,key in (('fragments','fragments'),('whole_cells','whole_cells'),('substrate_fragments','substrate_fragments'),('selected_objects','selected'),('activated_native','activated_native'),('cleanup_owners','cleanup_owners')):
                m.require(db.execute('DELETE FROM '+table+' WHERE dim=? AND uuid=?',(dim,uid)).rowcount==len(record[key]),'Original object derived rows changed during atomic section fallback')
        for root,record in sorted(wide_records.items()):
            m.require(db.execute('DELETE FROM native_visual_fragments WHERE dim=? AND rx=? AND ry=? AND rz=?',(dim,*root)).rowcount==len(record['fragments']),'Original wide fragments changed')
            m.require(db.execute('DELETE FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?',(dim,*root)).rowcount==len(record['rootRows']),'Original wide root changed')
        for row in plan:
            args=(dim,*row['position'])
            if row['retire']:
                m.require(db.execute('DELETE FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=? AND processed=?',(*args,row['before']['queue'][0]['processed'])).rowcount==1,'Retire only the exact undemanded original section work')
            if not any(row['remainingDemands'].values()):
                m.require(db.execute('DELETE FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args).rowcount==len(row['before']['bridge_cells']),'Delete only the exact undemanded original section bridge')
        domains=[]
        for uid,source in sorted(sources.items()):
            actual=context.original_domain(dim,uid);m.require(actual==source,'Original object source/data changed before commit');domains.append(actual)
            for row in source['domain']:db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(dim,*row['position'],uid,LOCAL_ORIGINAL_REASON))
        for root,snapshot in sorted(native_cells.items()):
            m.require(original_native_cell(context,dim,root,snapshot['sourceState'],unreplaced=True)==snapshot,'Original wide root/data changed before commit');db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(dim,*root,snapshot['uuid'],LOCAL_ORIGINAL_REASON))
        m.require(context.zone_snapshot(dim,minimum)==zone and m.meta(db,'nativeComposeCursor')==cursor and m.meta(db,'nativeLocalRetention')==retention,'Original source section/cursor/history changed')
        event={'schemaVersion':1,'kind':'AUTOMATIC_DIRECT_ORIGINAL_NATIVE_MEMBER_RETENTION','sourceBinding':m.meta(db,'binding'),'sourceFiles':source_files(m),'previous':head,'sourceZone':zone,
               'originalDomains':domains,'originalNativeCells':list(native_cells.values()),'zoneObjectWorkBefore':objects,'zoneWideWorkBefore':{m.canonical(root):record for root,record in wide_records.items()},'pointPlan':plan,'beforeCounts':before,'afterCounts':n.counts(db),
               'cursor':cursor,'retentionHead':retention,'processedRowsReset':0,'ownerGraphTraversal':False,'mapWrites':0}
        if finalization_contact is not None:event['reservedDomainContact']=finalization_contact
        sequence=(head['events'] if head else 0)+1;digest=m.digest(event);db.execute('INSERT INTO source_local_retentions VALUES(?,?,?)',(sequence,m.canonical(event),digest));m.meta(db,'sourceLocalRetentionJournal',{'schemaVersion':1,'events':sequence,'head':digest,'sourceBinding':event['sourceBinding']})
        if finalization_contact is None:db.commit()
        return True
    except BaseException:db.rollback();raise


def stage_reserved_source_domain_queues(m,db,context):
    """Stage finite saved-domain q1 contacts; caller validates before commit."""
    m.require(db.in_transaction and m.meta(db,'nativeComposeCursor')['phase']=='NATIVE_TRANSFER' and not m.meta(db,'applyStarted') and not m.meta(db,'compositionGeneration') and not db.execute('SELECT 1 FROM placements LIMIT 1').fetchone(),'Reserved source-domain staging requires the unfinished caller transaction')
    m.require(not db.execute('SELECT 1 FROM cell_queue WHERE processed=0 LIMIT 1').fetchone(),'Finish pending native work before reserved-domain staging')
    contacts=[]
    for dim,uid,encoded,digest in db.execute('SELECT * FROM source_visual_domains ORDER BY dim,uuid'):
        domain=m.json.loads(encoded);m.require(m.digest(domain)==digest and domain['bindings']==context.bindings and domain['object']['dim']==dim and domain['object']['uuid']==uid,'Saved original source-domain authority changed')
        for point in domain['domain']:
            queue=db.execute('SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',(dim,*point['position'])).fetchone()
            if not queue:continue
            m.require(queue==(1,),'Reserved source-domain queue is not completed native work')
            contacts.append({'dimension':dim,'position':point['position'],'uuid':uid,'domainDigest':digest});m.require(len(contacts)<=4096,'Reserved source-domain contacts exceed finite bound')
    sections=0
    for contact in contacts:
        dim=contact['dimension'];at=tuple(contact['position'])
        if not db.execute('SELECT 1 FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone():continue
        state,_,_=context.source_point(dim,at)
        error=RuntimeError('Completed work meets a saved original source domain\nProtected source contact requires a direct compatible/local fallback: '+m.canonical({'dimension':dim,'position':list(at),'incoming':None}))
        m.require(automatic_source_zone_retention(m,db,context,dim,at,state,error,finalization_contact=contact),'Reserved source-domain original section was not staged')
        m.require(not db.execute('SELECT 1 FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone(),'Reserved source-domain contact still has replacement work');sections+=1
    return {'contacts':contacts,'sectionsRetained':sections}


def automatic_direct_native_retention(m,db,context,dim,at,state,error):
    """Rollback caller batch first; retain only directly conflicting originals.

    Completed empty queue rows are retired, never reset. An independently
    activated native root is a demand even when its visual slot is empty.
    """
    prefix='A consumed native helper belongs to a retained/protected local object: '
    incoming=None;contact=tuple(at);protected=None;whole_contact=[];wide_cells=None
    if str(error).startswith(prefix):target=__import__('ast').literal_eval(str(error)[len(prefix):])
    else:
        marker='\nProtected source contact requires a direct compatible/local fallback: '
        if marker not in str(error):return False
        detail=m.json.loads(str(error).split(marker,1)[1]);incoming=detail['incoming']
        if incoming.get('kind') not in ('legacy_native','wide_native'):return False
        m.require(detail['dimension']==dim,'Protected source contact dimension changed')
        contact=tuple(detail['position']);protected=detail.get('protected');whole_contact=detail.get('wholeRows',[]);target=(dim,protected['uuid'] if protected else None)
    m.require(len(target) in (2,4) and target[0]==dim,'Protected original conflict identity changed')
    m.require(not db.in_transaction,'Local original fallback requires a rolled-back native batch')
    db.execute('BEGIN IMMEDIATE')
    try:
        m.require(m.meta(db,'nativeComposeCursor')['phase']=='NATIVE_TRANSFER' and not m.meta(db,'applyStarted') and not m.meta(db,'compositionGeneration'),'Local original fallback requires the unfinished native phase')
        m.require(not db.execute('SELECT 1 FROM placements LIMIT 1').fetchone(),'Local original fallback cannot alter finalized placements')
        m.require(db.execute('SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',(dim,*at)).fetchone()==(0,),'Reload the actual committed pending native contact')
        actual,_,_=context.source_point(dim,at);m.require(actual==state,'Original cached native contact changed')
        foreign_contact=None
        if incoming is None:
            tree,tree_points=local_original_scope(context,dim,target[1],native=True)
            m.require(contact in tree_points,'The contacted retained source point must be an actual member/domain')
            m.require((tree['kind'],tree['reason'])==tuple(target[2:]),'Actual retained native object changed')
            origins=context.native_origins(dim,at,state)
            m.require(any(row['uuid']==tree['uuid'] and row['member'] for row in origins),'Pending contact is not an actual source native member')
        elif protected:
            foreign_contact=unchanged_protected_contact(context,dim,contact,protected)
            tree=foreign_contact['object'];tree_points=set()
            if tree['kind']!='native_cell':tree,tree_points=local_original_scope(context,dim,tree['uuid'])
        else:tree={'dim':dim,'uuid':None};tree_points=set()
        owner_ids=set();native_ids=set()
        for cell in tree_points:
            saved=point(m,db,dim,cell)
            m.require(not any(saved[key] for key in ('native_visual_fragments','native_visual_roots','placements')),'Protected native member has an unsupported direct demand; no owner graph fallback')
            native_ids.update(row['uuid'] for row in saved['substrate_fragments'])
            owner_ids.update(row['uuid'] for row in saved['fragments']+saved['whole_cells'])
        if whole_contact:
            m.require(whole_contact==point(m,db,dim,contact)['whole_cells'],'Actual dedicated whole-role contact changed')
            owner_ids.update(row['uuid'] for row in whole_contact)
        if incoming is not None and incoming['kind']=='legacy_native':native_ids.add(incoming['uuid'])
        m.require(len(native_ids)<=4096 and tree['uuid'] not in native_ids,'Direct original-native donor bound/identity exceeded')
        scopes=[(tree,tree_points)] if tree_points else [];fragments={};whole_roles={};selected={};retracted={};native_cells=[];union=set(tree_points)
        if incoming is not None and incoming['kind']=='wide_native':
            root=tuple(incoming['root']);m.require(root==tuple(at) and incoming['state']==state,'Wide native source root/state changed')
            close=context.exports is None;exports=context.exports or sqlite3.connect('file:'+Path(context.bindings['poseFragments']['path']).as_posix()+'?mode=ro',uri=True)
            try:
                saved=exports.execute('SELECT data FROM native WHERE state=?',(m.canonical(state),)).fetchone();m.require(saved and m.json.loads(saved[0]).get('nativeVisualComplete',True),'Wide source requires its complete actual native model')
                offsets=exports.execute('SELECT x,y,z FROM native_fragments WHERE state=?',(m.canonical(state),)).fetchall()
            finally:
                if close:exports.close()
            m.require(0<len(offsets)<=4096,'Wide native visual source must be finite/complete');wide_cells={tuple(root[i]+offset[i] for i in range(3)) for offset in offsets};m.require(contact in wide_cells,'Whole/protected contact lies outside actual wide native source')
            m.require(not db.execute('SELECT 1 FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?',(dim,*root)).fetchone() and not db.execute('SELECT 1 FROM native_visual_fragments WHERE dim=? AND rx=? AND ry=? AND rz=? LIMIT 1',(dim,*root)).fetchone(),'Rolled-back wide source must have zero committed derived work')
            roots=n.row_dict(db,'SELECT * FROM objects INDEXED BY object_root WHERE dim=? AND x=? AND z=? AND y=?',(dim,root[0],root[2],root[1]));matches=[]
            for obj in roots:
                encoded=db.execute('SELECT data FROM poses WHERE key=?',(obj['pose'],)).fetchone();pose=m.json.loads(encoded[0])
                if pose['registryId']==state['registryId'] and pose.get('properties',{})==state.get('properties',{}):matches.append(obj)
            m.require(len(matches)<=4096,'Wide source root identity bound exceeded')
            for obj in matches:
                if db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?',(dim,obj['uuid'])).fetchone():owner_ids.add(obj['uuid'])
                elif obj['kind']=='substrate':
                    m.require(len(matches)==1,'Wide source has ambiguous pending native identities');native_ids.add(obj['uuid']);incoming={**incoming,'uuid':obj['uuid'],'pose':obj['pose']}
                else:
                    original,members=local_original_scope(context,dim,obj['uuid']);scopes.append((original,members));union.update(members)
                    m.require(len(union)<=32768,'Direct original union exceeds finite bound')
            if not matches:native_cells.append(original_native_cell(context,dim,root,state));union.add(root)
            union.update(wide_cells)
            for obj,members in scopes:
                for cell in members:
                    demands=point(m,db,dim,cell);native_ids.update(row['uuid'] for row in demands['substrate_fragments']);owner_ids.update(row['uuid'] for row in demands['fragments']+demands['whole_cells'])
        for snapshot in native_cells:
            demands=point(m,db,dim,snapshot['position']);native_ids.update(row['uuid'] for row in demands['substrate_fragments']);owner_ids.update(row['uuid'] for row in demands['fragments']+demands['whole_cells'])
        m.require(0<len(owner_ids)+len(native_ids)+len(native_cells)+len(scopes) and len(owner_ids|native_ids|{obj['uuid'] for obj,_ in scopes}|{row['uuid'] for row in native_cells})<=4096,'Automatic fallback needs bounded directly incoming native/static/whole owners')
        while native_ids-set(retracted) or owner_ids-set(selected):
            m.require(len(owner_ids|native_ids|{obj['uuid'] for obj,_ in scopes}|{row['uuid'] for row in native_cells})<=4096 and tree['uuid'] not in native_ids,'Direct original native/static/whole physical bound/identity exceeded')
            for uid in sorted(native_ids-set(retracted)):
                obj,members=local_original_scope(context,dim,uid)
                m.require(obj['kind']=='substrate' and (dim,uid) not in context.local_native_domains(),'Only a directly active original native donor may be retracted')
                activation=n.row_dict(db,'SELECT * FROM activated_native WHERE dim=? AND uuid=?',(dim,uid))
                own=n.row_dict(db,'SELECT * FROM substrate_fragments WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',(dim,uid))
                expected={(obj['x']+dx,obj['y']+dy,obj['z']+dz,dx,dy,dz) for dx,dy,dz in db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=?',(obj['pose'],))}
                if wide_cells is not None:expected={(*cell,cell[0]-obj['x'],cell[1]-obj['y'],cell[2]-obj['z']) for cell in wide_cells}
                m.require(0<len(expected)<=4096,'Direct native requires its complete bounded source visual footprint')
                pending=incoming is not None and uid==incoming['uuid']
                if pending:
                    m.require(not activation and not own and incoming['pose']==obj['pose'] and incoming['root']==[obj[k] for k in ('x','y','z')],'Rolled-back incoming native must have zero committed activation/derived rows and its exact source frame')
                    m.require(tuple(at) in members and contact in {row[:3] for row in expected} and (wide_cells is not None or any(row['uuid']==uid and row['member'] for row in context.native_origins(dim,at,state))),'The actual pending seed/native origin and protected visual contact changed')
                else:
                    m.require(activation==[{'dim':dim,'uuid':uid}] and 0<len(own)<=4096 and expected=={(row['x'],row['y'],row['z'],row['dx'],row['dy'],row['dz']) for row in own} and all(row['pose']==obj['pose'] for row in own),'Direct native donor needs its exact committed activation/complete derived rows')
                for table in ('selected_objects','fragments','whole_cells','cleanup_owners'):
                    hint=' INDEXED BY fragment_owner' if table=='fragments' else ''
                    m.require(not db.execute('SELECT 1 FROM '+table+hint+' WHERE dim=? AND uuid=? LIMIT 1',(dim,uid)).fetchone(),'Native donor has an unsupported second conversion mechanism: '+table)
                # Discover only direct demands on real source members.
                for cell in members:
                    demands=point(m,db,dim,cell);native_ids.update(row['uuid'] for row in demands['substrate_fragments']);owner_ids.update(row['uuid'] for row in demands['fragments']+demands['whole_cells'])
                retracted[uid]={'object':obj,'activatedRow':activation[0] if activation else None,'substrateFragments':own,'pendingAfterBatchRollback':pending}
                scopes.append((obj,members));union.update(members);union.update(row[:3] for row in expected)
                m.require(len(union)<=32768,'Direct original union exceeds finite bound')
            # Extend only through real source members, never through visual-only
            # fragment intersections. Both donor groups remain finitely bounded.
            while owner_ids-set(selected):
                m.require(len(owner_ids|native_ids|{obj['uuid'] for obj,_ in scopes}|{row['uuid'] for row in native_cells})<=4096,'Direct physical static/whole owner bound exceeded')
                uid=min(owner_ids-set(selected))
                obj,members=local_original_scope(context,dim,uid)
                rows=n.row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid))
                m.require(obj['kind'] in ('static','whole') and len(rows)==1 and rows[0]['kind']==obj['kind'] and rows[0]['pose']==obj['pose'],'Only directly selected static/whole decoration may become original')
                own=n.row_dict(db,'SELECT * FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',(dim,uid))
                roles=n.row_dict(db,'SELECT * FROM whole_cells WHERE dim=? AND uuid=? ORDER BY x,y,z LIMIT 4097',(dim,uid))
                m.require(len(own)<=4096 and len(roles)<=4096 and (bool(roles) if obj['kind']=='whole' else bool(own) and not roles),'Direct owner needs its complete bounded own fragments/whole roles')
                for table in ('activated_native','cleanup_owners','substrate_fragments'):
                    m.require(not db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND uuid=? LIMIT 1',(dim,uid)).fetchone(),'Direct owner has another conversion mechanism: '+table)
                fragments[uid]=own;whole_roles[uid]=roles;selected[uid]=rows[0];scopes.append((obj,members));union.update(members);union.update((row['x'],row['y'],row['z']) for row in own+roles)
                m.require(len(union)<=32768,'Direct original union exceeds finite bound')
                for cell in members:
                    demands=point(m,db,dim,cell);native_ids.update(row['uuid'] for row in demands['substrate_fragments']);owner_ids.update(row['uuid'] for row in demands['fragments']+demands['whole_cells'])
        m.require(len(union)<=32768,'Direct original union exceeds finite bound')
        strict=set().union(*(cells for _,cells in scopes));strict.update(tuple(row['position']) for row in native_cells);excluded=owner_ids|native_ids|{tree['uuid']};plan=[]
        before=n.counts(db);cursor=m.meta(db,'nativeComposeCursor');retention=m.meta(db,'nativeLocalRetention');head=m.meta(db,'sourceLocalRetentionJournal')
        for cell in sorted(union):
            saved=point(m,db,dim,cell);m.require(not saved['placements'],'No final placement may be locally retired')
            demands={key:saved[key] for key in ('substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots')}
            demands['substrate_fragments']=[row for row in saved['substrate_fragments'] if row['uuid'] not in native_ids]
            demands['fragments']=[row for row in saved['fragments'] if row['uuid'] not in owner_ids]
            demands['whole_cells']=[row for row in saved['whole_cells'] if row['uuid'] not in owner_ids]
            original,_,_=context.source_point(dim,cell);foreign=[]
            for origin in context.native_origins(dim,cell,original):
                if origin['uuid'] not in excluded:
                    active=n.row_dict(db,'SELECT * FROM activated_native WHERE dim=? AND uuid=?',(dim,origin['uuid']))
                    if active:foreign.extend(active)
            demands['foreignActivatedNative']=foreign;needed=any(demands.values())
            m.require(cell not in strict or not needed,'Independent demand on a real original member; direct fallback stops without traversing that owner: '+str((dim,cell,demands)))
            m.require(saved['queue'] in ([],[{'processed':0}],[{'processed':1}]),'Unknown direct queue state')
            m.require(not needed or saved['queue'],'Independent demand lost its existing queue')
            plan.append({'position':list(cell),'before':saved,'remainingDemands':demands,'retire':bool(saved['queue']) and not needed})
        for uid,donor in sorted(retracted.items()):
            m.require(db.execute('DELETE FROM substrate_fragments WHERE dim=? AND uuid=?',(dim,uid)).rowcount==len(donor['substrateFragments']),'Only complete direct native derived rows may be retracted')
            m.require(db.execute('DELETE FROM activated_native WHERE dim=? AND uuid=?',(dim,uid)).rowcount==(donor['activatedRow'] is not None),'Retract only the exact existing direct donor activation; pending incoming must remain absent')
        for uid in sorted(owner_ids):
            m.require(db.execute('DELETE FROM fragments WHERE dim=? AND uuid=?',(dim,uid)).rowcount==len(fragments[uid]),'Direct fragments changed during atomic fallback')
            m.require(db.execute('DELETE FROM whole_cells WHERE dim=? AND uuid=?',(dim,uid)).rowcount==len(whole_roles[uid]),'Direct complete whole roles changed during atomic fallback')
            m.require(db.execute('DELETE FROM selected_objects WHERE dim=? AND uuid=?',(dim,uid)).rowcount==1,'Direct selected owner changed during atomic fallback')
        for row in plan:
            if row['retire']:
                args=(dim,*row['position']);processed=row['before']['queue'][0]['processed']
                m.require(db.execute('DELETE FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=? AND processed=?',(*args,processed)).rowcount==1,'Retire only the exact undemanded pending/completed row')
                db.execute('DELETE FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args)
        context.domains.clear();context.strict=None;domains=[]
        for obj,cells in scopes:
            domain=context.original_domain(dim,obj['uuid'])
            m.require({tuple(row['position']) for row in domain['domain']}==cells,'Complete source runtime/service domain differs from the finite local scope')
            domains.append(domain)
            for cell in sorted(cells):db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(dim,*cell,obj['uuid'],LOCAL_ORIGINAL_REASON))
        for uid in sorted(owner_ids):m.issue(db,'protected_local_overlap',dim,tuple(selected[uid][key] for key in ('x','y','z')),{'uuid':uid,'reason':LOCAL_ORIGINAL_REASON,'retainedNative':tree['uuid'],'policy':'Real source members only; bounded local physical contacts'})
        for snapshot in native_cells:
            m.require(original_native_cell(context,dim,snapshot['position'],snapshot['sourceState'],unreplaced=True)==snapshot,'Original wide source native cell changed')
            db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(dim,*snapshot['position'],snapshot['uuid'],LOCAL_ORIGINAL_REASON))
        if foreign_contact:m.require(unchanged_protected_contact(context,dim,contact,protected,unreplaced=True)==foreign_contact,'Foreign protected object/data/provenance changed during local incoming retention')
        event={'schemaVersion':1,'kind':'AUTOMATIC_DIRECT_ORIGINAL_NATIVE_MEMBER_RETENTION','sourceBinding':m.meta(db,'binding'),'sourceFiles':source_files(m),'previous':head,
               'native':tree,'incomingNative':incoming,'protectedContact':foreign_contact,'selectedOwnersBefore':selected,'fragmentsBefore':fragments,'wholeRolesBefore':whole_roles,'retractedNatives':retracted,'originalDomains':domains,'originalNativeCells':native_cells,'pointPlan':plan,'beforeCounts':before,'afterCounts':n.counts(db),
               'cursor':cursor,'retentionHead':retention,'processedRowsReset':0,'ownerGraphTraversal':False,'mapWrites':0}
        m.require(m.meta(db,'nativeComposeCursor')==cursor and m.meta(db,'nativeLocalRetention')==retention,'Local fallback changed the accepted native cursor/retention')
        sequence=(head['events'] if head else 0)+1;digest=m.digest(event)
        db.execute('INSERT INTO source_local_retentions VALUES(?,?,?)',(sequence,m.canonical(event),digest))
        m.meta(db,'sourceLocalRetentionJournal',{'schemaVersion':1,'events':sequence,'head':digest,'sourceBinding':event['sourceBinding']})
        db.commit();context.domains.clear();context.strict=None
        return True
    except BaseException:
        db.rollback();context.domains.clear();context.strict=None;raise


def validate_source_local_retentions(m,db,finalized=False,source_files_validator=None):
    journal=m.meta(db,'sourceLocalRetentionJournal')
    if not journal:return
    check_sources=source_files_validator or (lambda files:require_source_files(m,db,files));context=SourceVisualReservations(m,db);previous=None;tuples=set();count=0
    for sequence,encoded,digest in db.execute('SELECT sequence,data,digest FROM source_local_retentions ORDER BY sequence'):
        event=m.json.loads(encoded);count+=1
        m.require(sequence==count and m.digest(event)==digest and event['previous']==previous and event['sourceBinding']==m.meta(db,'binding') and event['kind']=='AUTOMATIC_DIRECT_ORIGINAL_NATIVE_MEMBER_RETENTION' and event['processedRowsReset']==0 and event['ownerGraphTraversal'] is False,'Automatic local original journal changed')
        source_tuple=m.canonical(event['sourceFiles'])
        if source_tuple not in tuples:check_sources(event['sourceFiles']);tuples.add(source_tuple)
        zone=event.get('sourceZone')
        if zone:
            m.require(context.zone_snapshot(zone['dimension'],zone['minimum'])==zone,'Original source section state/typed BE/cache binding changed')
            low,high=zone['minimum'],zone['maximum'];args=(zone['dimension'],low[0],high[0],low[1],high[1],low[2],high[2])
            for table in ('cell_queue','placements','fragments','whole_cells','substrate_fragments','native_visual_fragments','native_visual_roots','bridge_cells'):
                m.require(not db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ? AND z BETWEEN ? AND ? LIMIT 1',args).fetchone(),'Original source section acquired replacement work: '+table)
            for key,record in event.get('zoneWideWorkBefore',{}).items():
                root=m.json.loads(key)
                m.require(not db.execute('SELECT 1 FROM native_visual_roots WHERE dim=? AND x=? AND y=? AND z=?',(zone['dimension'],*root)).fetchone() and not db.execute('SELECT 1 FROM native_visual_fragments WHERE dim=? AND rx=? AND ry=? AND rz=? LIMIT 1',(zone['dimension'],*root)).fetchone(),'Original wide native derived work re-entered after section retention')
        for domain in event['originalDomains']:
            obj=domain['object'];dim=obj['dim'];uid=obj['uuid']
            if domain.get('runtimeFootprintEnvelope'):context.__dict__.setdefault('_saved_envelopes',{})[obj['pose']]=domain['runtimeFootprintEnvelope']
            actual=context.original_domain(dim,uid) if domain['mechanism']=='source_original_snapshot' else context.owner_domain(dim,uid)
            m.require(context.domain_equal(actual,domain),'Automatic original root/member/data/payload state changed')
            for row in domain['domain']:
                m.require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',(dim,*row['position'],uid)).fetchone(),'Automatic original member protection disappeared')
        for cell in event.get('originalNativeCells',[]):
            m.require(original_native_cell(context,cell['dimension'],cell['position'],cell['sourceState'],unreplaced=True)==cell,'Original native source cell/data changed')
            m.require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',(cell['dimension'],*cell['position'],cell['uuid'])).fetchone(),'Original native source-cell protection disappeared')
        foreign=event.get('protectedContact')
        if foreign:m.require(unchanged_protected_contact(context,foreign['dimension'],foreign['position'],foreign['protectedRow'],unreplaced=True)==foreign,'Already-retained foreign protected object/data changed')
        previous={'schemaVersion':1,'events':count,'head':digest,'sourceBinding':event['sourceBinding']}
    m.require(previous==journal,'Automatic original journal is incomplete')


def source_files(m):
    return {'migrate':m.sha(m.__file__),'nativeResume':m.sha(Path(__file__).with_name('native_resume.py')),
            'nativeCertificates':m.sha(__file__)}


def bound(m,value):return n.read_bound(m,value)


def immutable_json(m,path,value):
    if Path(path).exists():m.require(m.read(path)==value,'An immutable preview/proof already uses this path; choose a fresh path')
    else:m.atomic_json(path,value)


def source_lineage(m,db,binding):
    """Each code change is independently reviewed; old tuple2/tuple3 stay bound."""
    reference=binding['transition'];expected=binding['sourceFiles'];result=[];seen=set()
    while reference:
        m.require(reference['sha256'] not in seen,'Source transition cycle');seen.add(reference['sha256'])
        transition=bound(m,reference)
        m.require(transition['status']=='PASS' and transition['kind']=='NATIVE_CONSUMER_SOURCE_ONLY_TRANSITION' and transition['toSourceFiles']==expected,'Native consumer source transition is invalid')
        m.require(transition['sourceBinding']==m.meta(db,'binding'),'Source upgrade immutable binding changed')
        for row in transition['archivedSources'].values():m.require(m.sha(row['path'])==row['sha256'],'Archived native consumer source changed')
        proof=bound(m,transition['sourceProof']);bound(m,transition['failedRun'])
        m.require(proof['sourceFiles']==transition['toSourceFiles'] and proof['oldSourceFiles']==transition['fromSourceFiles'],'Reviewed source tuple lineage changed')
        cursor=m.meta(db,'nativeLocalRetention');ancestors=set()
        while cursor:
            m.require(cursor['sha256'] not in ancestors,'Native retention ancestor cycle');ancestors.add(cursor['sha256'])
            if cursor==transition['parentRetention']:break
            cursor=bound(m,cursor).get('previousRetention')
        m.require(cursor==transition['parentRetention'],'Reviewed source-upgrade parent is not an immutable ancestor of the active retention head')
        result.append(transition);expected=transition['fromSourceFiles'];reference=transition.get('previousSourceTransition')
    m.require(result,'Missing source transition lineage')
    return result


def active_binding(m,db):
    binding=m.meta(db,'nativeResumeSourceBinding')
    m.require(binding and binding['sourceFiles']==source_files(m),'Review and execute an explicit native consumer source upgrade first')
    return source_lineage(m,db,binding)[0]


def require_source_files(m,db,wanted):
    current=source_files(m);binding=m.meta(db,'nativeResumeSourceBinding')
    if not binding:
        m.require(wanted=={k:current[k] for k in ('migrate','nativeResume')},'Historical native consumer bytes require an explicit source transition')
        return
    active_binding(m,db)
    allowed=[current]
    for transition in source_lineage(m,db,binding):allowed.extend(reviewed_upgrade_source_files(m,bound(m,transition['sourceProof']),transition['fromSourceFiles'],transition['failedRun']))
    m.require(wanted in allowed,'Retention/failure source tuple is outside the reviewed immutable lineage')


def failed_evidence(m,db,reference):
    failure=bound(m,reference);arguments=failure.get('args',[])
    m.require(failure.get('status')=='FAIL' and failure.get('exitCode')==1 and arguments and arguments[-1]=='resume-native-compose','A real failed native-phase resume is required')
    database=Path(db.execute('PRAGMA database_list').fetchone()[2])
    m.require(len(arguments)==3 and arguments[0]=='--plan' and m.checked(arguments[1])==m.checked(database.parent),'Failure belongs to another disk plan')
    rows={Path(row['archive']).name:row for row in failure['inputSources'].values()}
    m.require('migrate.py' in rows and 'native_resume.py' in rows and 'context_stage.py' in rows,'Failed native source archive is incomplete')
    for row in rows.values():m.require(m.sha(row['archive'])==row['sha256'],'Failed native source archive changed')
    m.require(m.sha(Path(m.__file__).with_name('context_stage.py'))==rows['context_stage.py']['sha256'],'Accepted functional/context source changed')
    files={'migrate':rows['migrate.py']['sha256'],'nativeResume':rows['native_resume.py']['sha256']}
    if 'native_certificates.py' in rows:files['nativeCertificates']=rows['native_certificates.py']['sha256']
    return failure,rows,files


def reviewed_upgrade_source_files(m,proof,old_files,failure_reference):
    """Direct reviewed bootstrap generations, never an inferred source chain."""
    m.require(isinstance(proof['sourceFiles'],dict),'Reviewed source tuple must be a dictionary');keys=set(proof['sourceFiles'])
    m.require(keys in ({'migrate','nativeResume'},{'migrate','nativeResume','nativeCertificates'}),'Unknown historical/current reviewed source keys')
    def checked(files):
        m.require(isinstance(files,dict) and set(files)==keys and all(isinstance(value,str) and len(value)==64 and all(char in '0123456789abcdef' for char in value) for value in files.values()),'Reviewed source tuple has an invalid key/SHA shape')
        return files
    allowed=[old_files,checked(proof['sourceFiles'])];references=proof.get('reviewedIntermediateSourceProofs',[])
    m.require(isinstance(references,list) and len(references)<=16,'Only a bounded direct list of reviewed source proofs may enter an upgrade')
    for reference in references:
        intermediate=bound(m,reference)
        m.require(intermediate['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and intermediate['kind']=='NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE' and intermediate['oldSourceFiles']==old_files and intermediate['actualFailedRun']==failure_reference,'Intermediate source proof belongs to another reviewed failure/generation')
        allowed.append(checked(intermediate['sourceFiles']))
    return allowed


def upgrade_preview(a,m,db):
    n.require_unfinalized(m,db)
    parent=m.meta(db,'nativeLocalRetention');receipt=bound(m,parent)
    m.require(receipt['status']=='PASS' and receipt['kind']=='EXPLICIT_FINITE_NATIVE_RETENTION','A committed native retention must precede its source upgrade')
    previous_binding=m.meta(db,'nativeResumeSourceBinding')
    proof_reference={'path':str(m.checked(a.source_proof)),'sha256':a.source_proof_sha256};proof=bound(m,proof_reference)
    m.require(proof['status']=='PASS_SOURCE_AND_TARGETED_FIXTURES_ONLY' and proof['kind']=='NATIVE_CERTIFICATE_CONSUMER_SOURCE_UPGRADE' and proof['sourceFiles']==source_files(m),'Current reviewed source/fixture proof is required')
    failure_reference=n.ref(m,a.failed_run);failure,rows,old_files=failed_evidence(m,db,failure_reference)
    m.require(old_files==proof['oldSourceFiles'],'The source upgrade must begin at the exact actual failed historical consumer')
    allowed=reviewed_upgrade_source_files(m,proof,old_files,failure_reference)
    if previous_binding:
        m.require(previous_binding['sourceFiles']==old_files,'The failed source is not the active preceding source generation')
        for transition in source_lineage(m,db,previous_binding):allowed.extend(reviewed_upgrade_source_files(m,bound(m,transition['sourceProof']),transition['fromSourceFiles'],transition['failedRun']))
    m.require(receipt['sourceFiles'] in allowed,'The retention head source is not a reviewed source-lineage ancestor')
    m.require(receipt['semanticBindings']==n.snapshot_bindings(m,db),'Original discovery/classification/function inputs changed')
    # The ordinary retained-state guards still run in full. Only the reviewed
    # historical source tuple is admitted during this explicit transition.
    n.validate_resume(m,db,m.checked(a.plan),source_files_validator=lambda files:m.require(files in allowed,'Source-upgrade ancestor used another unreviewed consumer'))
    cursor=m.meta(db,'nativeComposeCursor')
    m.require(cursor and cursor['phase']=='NATIVE_TRANSFER' and cursor['retention']==parent,'Source upgrade requires the existing durable native commit boundary')
    return {'schemaVersion':1,'status':'NATIVE_SOURCE_UPGRADE_PREVIEW','sourceBinding':m.meta(db,'binding'),
            'semanticBindings':n.snapshot_bindings(m,db),'parentRetention':parent,'cursor':cursor,
            'previousSourceTransition':previous_binding['transition'] if previous_binding else None,
            'beforeCounts':n.counts(db),'fromSourceFiles':old_files,'toSourceFiles':source_files(m),
            'failedRun':failure_reference,'failedArchives':rows,'sourceProof':proof_reference,
            'databaseWrites':0,'mapWrites':0}


def upgrade(a,m):
    plan=m.checked(a.plan)
    db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+('?mode=rw' if a.execute else '?mode=ro'),uri=True)
    try:
        db.execute('PRAGMA cache_size=-2048')
        if a.execute:db.execute('PRAGMA synchronous=FULL')
        attach_source_journal_original(m,db)
        db.execute('BEGIN IMMEDIATE' if a.execute else 'BEGIN')
        if a.execute:
            m.require(a.expected_preview_sha256 and m.sha(a.preview)==a.expected_preview_sha256,'Execute source upgrade only with the exact reviewed preview SHA')
            if m.meta(db,'nativeResumeSourceBinding') and m.meta(db,'nativeResumeSourceBinding')['sourceFiles']==source_files(m):
                transition=active_binding(m,db);m.require(transition['preview']['sha256']==a.expected_preview_sha256,'Another source upgrade owns this binding');db.rollback();return
        preview=upgrade_preview(a,m,db)
        if not a.execute:immutable_json(m,a.preview,preview);return
        m.require(preview==m.read(a.preview),'Native state/cursor/bindings changed after source-upgrade preview')
        directory=plan/'full/native-consumer-source-upgrades'/a.expected_preview_sha256;archive=directory/'after';archive.mkdir(parents=True,exist_ok=True)
        archived={}
        for name,filename in (('migrate','migrate.py'),('nativeResume','native_resume.py'),('nativeCertificates','native_certificates.py')):
            source=Path(m.__file__).with_name(filename);destination=archive/filename;payload=source.read_bytes()
            if destination.exists():m.require(destination.read_bytes()==payload,'Source-upgrade archive changed')
            else:
                import os
                with destination.open('wb') as stream:stream.write(payload);stream.flush();os.fsync(stream.fileno())
            archived['after/'+name]=n.ref(m,destination)
        for name,row in preview['failedArchives'].items():archived['before/'+name]={'path':row['archive'],'sha256':row['sha256']}
        proof_copy=directory/'source-proof.json';immutable_json(m,proof_copy,bound(m,preview['sourceProof']))
        transition={**preview,'status':'PASS','kind':'NATIVE_CONSUMER_SOURCE_ONLY_TRANSITION','sourceProof':n.ref(m,proof_copy),
                    'preview':n.ref(m,a.preview),'archivedSources':archived,'afterCounts':n.counts(db),
                    'processedQueueRowsReset':0,'objectsChanged':0,'mapWrites':0}
        m.require(transition['beforeCounts']==transition['afterCounts'] and m.meta(db,'nativeComposeCursor')==preview['cursor'],'Source-only transition changed native work')
        output=directory/'transition.json'
        if output.exists():m.require(m.read(output)==transition,'Interrupted source transition proof changed')
        else:m.atomic_json(output,transition)
        reference=n.ref(m,output)
        m.meta(db,'nativeResumeSourceTransition',reference)
        m.meta(db,'nativeResumeSourceBinding',{'schemaVersion':1,'sourceFiles':source_files(m),'transition':reference})
        db.commit()
    finally:db.close()


def proof_files(m,certificate):
    m.require(certificate.get('schemaVersion')==1 and certificate.get('status')=='PASS' and certificate.get('kind')=='FINITE_NATIVE_RETENTION_CERTIFICATE_V1' and certificate.get('ownerGraphTraversal') is False,'Unknown/automatic native retention certificate')
    cases=certificate.get('cases',[]);m.require(0<len(cases)<=64,'Finite certificate case bound exceeded')
    for case in cases:
        native=case['native'];source=bound(m,native['sourceProof'])
        m.require(source.get('status')=='PASS' and source.get('sourceBinding')==certificate['sourceBinding'],'Native physical proof changed or belongs to another source')
        matches=[row for row in source.get('nativeComponents',[]) if row['uuid']==native['uuid']]
        m.require(len(matches)==1 and all(matches[0][key]==native[key] for key in ('pose','poseDataSha256','root','actualRootState','runtimeMemberCells')),'Physical source proof does not identify this exact native component/footprint')
        m.require(0<len(case['owners'])<=8,'Every native case requires an explicit bounded owner list')
        for owner in case['owners']:
            for name in ('sourceGeometryProof','classProof'):
                proof=bound(m,owner[name]);m.require(proof.get('status')=='PASS' and proof.get('sourceBinding')==certificate['sourceBinding'],'Original owner runtime proof changed')
                matches=[row for row in proof.get('owners',[]) if row['uuid']==owner['uuid']]
                keys=('pose','poseDataSha256','root','runtimeMemberCells')+ (('memberPolicies',) if 'memberPolicies' in owner else ())
                m.require(len(matches)==1 and all(matches[0][key]==owner[key] for key in keys),'Runtime proof does not identify this exact original owner/footprint/policies')
                if any(p['policy']!='original' for p in owner.get('memberPolicies',[])):
                    m.require(proof.get('finalCoverageRequired')=={'nativeFunctions':True,'carrierAudit':True,'affectedGameplayQa':True},'Projected member policies require final exact function/carrier/gameplay acceptance')


def member_policies(m,owner,classification):
    """Validate explicit source-backed modes; missing modes stay strict original."""
    members={tuple(at) for at in owner['runtimeMemberCells']}
    rows=owner.get('memberPolicies')
    if rows is None:return [{'position':list(at),'policy':'original'} for at in sorted(members)]
    m.require(len(rows)==len(members) and {tuple(row['position']) for row in rows}==members,'Every actual owner member needs exactly one finite member policy')
    points={tuple(row['position']):row for row in owner['pointDemands']}
    protected_ids=set(classification.get('protectedRegistryIds',[]))
    retained_poses={(p['registryId'],str(p['variant'])) for p in classification.get('poses',[]) if p['route']=='preserve'}
    for row in rows:
        at=tuple(row['position']);mode=row['policy']
        m.require(mode in ('original','nonroot_guest_overlay','root_existing_be_background'),'Unknown member replacement policy')
        m.require(row['pointDemand']==points[at],'Member policy demand evidence differs from the complete finite snapshot')
        if mode=='original':continue
        m.require(classification.get('schemaVersion')==1,'Projected member requires the bound actual classification')
        m.require(row['sourceState']['registryId'] not in protected_ids,'A protected v12 member cannot carry a projected background')
        state=row['sourceState'];preserved=(state['registryId'],str(state.get('properties',{}).get('variant','0'))) in retained_poses
        m.require(not preserved or mode=='root_existing_be_background' or row.get('existingBeBridge'),'A retained functional/tree/private source member requires an explicitly proved existing carrier')
        refs=row['storedContributions']
        m.require(refs and any(item['uuid']==owner['uuid'] for item in refs),'Projected member lacks this exact original owner ledger binding')
        if mode=='nonroot_guest_overlay':
            m.require(at!=tuple(owner['root']) and row.get('isRoot') is False and row.get('hasStoredDContribution') is True and row.get('rootUnchanged') is True,'Guest overlay is only a proved nonroot member with intact original root/ledger')
            bridge=row.get('existingBeBridge')
            if bridge:m.require(bridge.get('existingCompositeBE') is True and bridge.get('retainedResident') in (0,1),'A guest bridge requires an actual existing CompositeBE proof')
            m.require(row['sourceState']['registryId']!='bloodborne_dw:composite_cell' or bridge,'An actual CompositeBE helper needs an explicit existing carrier proof')
        else:
            m.require(at==tuple(owner['root']),'Resident background is only the exact original root')
            m.require(row.get('rootProof')=={'existingCompositeBE':True,'residentUuid':owner['uuid'],'privateOuterFields':[],
                                          'preserveOriginalRegistry':True,'nonV12':True},'Root background needs the exact existing resident/payload proof')
    return rows


def projection_bridge(m,policy):
    if policy['policy']=='root_existing_be_background':
        return {'host_state':m.canonical(policy['sourceState']),'retained_resident':1}
    bridge=policy.get('existingBeBridge')
    if bridge:return {'host_state':m.canonical(bridge['hostState']),'retained_resident':bridge['retainedResident']}
    return None


def projected_member_original_retention(m,db,owner,at,policy,source_files_validator=None):
    """An exact committed section may supersede a historical background policy.

    The caller still validates the complete local journal before any write.
    Only the matching event/domain is refreshed here, not every journal event
    for every projected member.
    """
    journal=m.meta(db,'sourceLocalRetentionJournal')
    if not journal or policy['policy'] not in ('nonroot_guest_overlay','root_existing_be_background'):return False
    head=db.execute('SELECT digest FROM source_local_retentions WHERE sequence=?',(journal['events'],)).fetchone()
    m.require(journal.get('schemaVersion')==1 and journal['sourceBinding']==m.meta(db,'binding') and head==(journal['head'],),'Original section journal head changed')
    check_sources=source_files_validator or (lambda files:require_source_files(m,db,files))
    for sequence,encoded,digest in db.execute('SELECT sequence,data,digest FROM source_local_retentions WHERE instr(data,?)>0 ORDER BY sequence DESC',(m.canonical(owner['uuid']),)):
        event=m.json.loads(encoded);zone=event.get('sourceZone')
        if not zone or zone['dimension']!=owner['dimension']:continue
        domains=[row for row in event['originalDomains'] if row['object']==owner['object'] and row['mechanism']=='source_original_snapshot' and any(point['position']==list(at) for point in row['domain'])]
        if not domains:continue
        m.require(len(domains)==1 and 1<=sequence<=journal['events'] and m.digest(event)==digest and event['sourceBinding']==journal['sourceBinding'] and event['kind']=='AUTOMATIC_DIRECT_ORIGINAL_NATIVE_MEMBER_RETENTION' and event['processedRowsReset']==0 and event['ownerGraphTraversal'] is False,'Projected original section authority changed')
        plans=[row for row in event['pointPlan'] if row['position']==list(at)]
        m.require(len(plans)==1 and plans[0]['retire'] is True and plans[0]['before']['queue'] in ([{'processed':0}],[{'processed':1}]) and not any(plans[0]['remainingDemands'].values()),'Projected background was not explicitly retired as undemanded original work')
        check_sources(event['sourceFiles']);domain=domains[0];context=SourceVisualReservations(m,db)
        if domain.get('runtimeFootprintEnvelope'):context.__dict__.setdefault('_saved_envelopes',{})[owner['object']['pose']]=domain['runtimeFootprintEnvelope']
        m.require(context.original_domain(owner['dimension'],owner['uuid'])==domain,'Projected member original source/data domain changed')
        args=(owner['dimension'],*at);current=point(m,db,owner['dimension'],at)
        m.require(not any(current[key] for key in ('queue','fragments','substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots','bridge_cells','placements')),'Original projected member acquired replacement work/carrier')
        m.require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=? AND reason=?',(*args,owner['uuid'],LOCAL_ORIGINAL_REASON)).fetchone(),'Original projected member protection disappeared')
        state,_,_=context.source_point(owner['dimension'],at)
        stored=n.row_dict(db,'SELECT * FROM old.stored_contributions WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',(*args,owner['uuid']))
        m.require(state==policy['sourceState'] and sorted(stored,key=m.canonical)==sorted(policy['storedContributions'],key=m.canonical),'Historical projected source state/ledger changed')
        return True
    return False


def validate_owner_members(m,db,owner,source_files_validator=None):
    """Legacy receipts have no policy list and retain their original guards."""
    policies={tuple(p['position']):p for p in owner.get('memberPolicies',[])}
    for at in owner['runtimeMemberCells']:
        args=(owner['dimension'],*at);policy=policies.get(tuple(at),{'policy':'original'})
        queue=db.execute('SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone()
        if policy['policy']=='original':
            m.require(not queue,'Retained actual D/E member acquired replacement work')
            m.require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=?',(*args,owner['uuid'])).fetchone(),'Retained actual D/E member protection disappeared')
        else:
            if not queue and projected_member_original_retention(m,db,owner,at,policy,source_files_validator):continue
            m.require(queue in ((0,),(1,)),'Projected member lost its independently demanded background queue')
            demands=any(db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=? LIMIT 1',args).fetchone() for table in ('fragments','substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots'))
            m.require(demands,'Projected member no longer has independent background work')
            expected=projection_bridge(m,policy)
            if expected:
                bridge=db.execute('SELECT host_state,retained_resident FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone()
                m.require(bridge==(expected['host_state'],expected['retained_resident']),'Projected member lost the exact existing CompositeBE carrier')


def point(m,db,dimension,position):
    args=(dimension,*position)
    result={'dimension':dimension,'position':list(position),
            'queue':n.row_dict(db,'SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args),
            'fragments':n.row_dict(db,'SELECT uuid,pose FROM fragments WHERE dim=? AND x=? AND y=? AND z=?',args)}
    for table in TABLES:result[table]=n.row_dict(db,'SELECT * FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?',args)
    return result


def certificate_preview(a,m,db):
    plan=m.checked(a.plan);n.validate_resume(m,db,plan);active_binding(m,db)
    reference={'path':str(m.checked(a.certificate)),'sha256':a.certificate_sha256};certificate=bound(m,reference)
    proof_files(m,certificate)
    m.require(certificate['sourceBinding']==m.meta(db,'binding'),'Certificate source changed')
    failure,archives,files=failed_evidence(m,db,certificate['failedRun']);require_source_files(m,db,files)
    encoded_log=m.checked(failure['log']).read_text(encoding='utf-8',errors='replace')
    m.require('Required native visual overhang meets a protected local object' in encoded_log and any(case['native']['uuid'] in encoded_log for case in certificate['cases']),'Certificate does not include its actual failed native conflict')
    snapshots={};normalized=[];owner_ids=set();owner_records={};native_ids=set()
    classification=bound(m,m.meta(db,'classification')) if m.meta(db,'classification') else {}
    def remember(saved):
        key=(saved['dimension'],*saved['position'])
        m.require(len(key)==4 and all(type(v)is int for v in key[1:]),'Finite points require exact integer coordinates')
        if key in snapshots:m.require(snapshots[key]==saved,'Conflicting duplicate certificate point')
        else:snapshots[key]=saved
    for case in certificate['cases']:
        m.require(case.get('classification')=='FINITE_PENDING_ORIGINAL_RETENTION','A declined/unreviewed case cannot enter a finite certificate')
        native=case['native'];dimension=native['dimension'];uid=native['uuid'];root=native['root']
        m.require((dimension,uid) not in native_ids,'Duplicate native identity');native_ids.add((dimension,uid))
        obj=db.execute('SELECT pose,x,y,z,kind FROM objects WHERE dim=? AND uuid=?',(dimension,uid)).fetchone()
        m.require(obj==(native['pose'],*root,'substrate'),'Native component is not the exact saved original substrate')
        pose=db.execute('SELECT data FROM poses WHERE key=?',(native['pose'],)).fetchone()
        m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==native['poseDataSha256'],'Native pose data changed')
        m.require(not db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',(dimension,uid)).fetchone(),'Cannot locally retain an already activated native component')
        m.require(0<len(native['runtimeMemberCells'])<=4096,'Native physical footprint must be finite and nonempty')
        for saved in case['nativePointDemands']:remember(saved)
        owners=[]
        for owner in case['owners']:
            ouid=owner['uuid'];token=(dimension,ouid)
            if token in owner_ids:
                m.require(owner_records[token]==owner,'A shared explicitly named owner has inconsistent certificate data')
                for saved in owner['pointDemands']:remember(saved)
                continue
            owner_ids.add(token);owner_records[token]=owner
            m.require(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',token)==[owner['object']] and n.row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND uuid=?',token)==[owner['selected']],'Original owner/selection changed')
            m.require(owner['selected']['kind']=='static','Only explicitly certified static owners may be locally retained')
            fragments=n.row_dict(db,'SELECT x,y,z,pose,dx,dy,dz FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=? ORDER BY x,y,z',token)
            m.require(fragments==owner['fragments'] and 0<len(fragments)<=4096,'Complete owner visual footprint changed')
            pose=db.execute('SELECT data FROM poses WHERE key=?',(owner['pose'],)).fetchone()
            m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==owner['poseDataSha256'],'Owner exact pose changed')
            m.require(owner['pose']==owner['object']['pose'] and owner['root']==[owner['object'][axis] for axis in ('x','y','z')],'Owner pose/root identity is inconsistent')
            m.require(0<len(owner['runtimeMemberCells'])<=4096,'Original owner runtime members must be explicit')
            for saved in owner['pointDemands']:remember(saved)
            owners.append({'dimension':dimension,'uuid':ouid,'object':owner['object'],'selected':owner['selected'],
                           'fragments':fragments,'runtimeMemberCells':owner['runtimeMemberCells'],
                           'memberPolicies':member_policies(m,owner,classification)})
        normalized.append({'policy':'generic_finite_original_members','retainedNative':{'dimension':dimension,'uuid':uid,'pose':native['pose'],
                          'root':root,'mustRemainOriginalCells':native['runtimeMemberCells']},'retainedOwners':owners})
    expected={(o['dimension'],f['x'],f['y'],f['z']) for case in normalized for o in case['retainedOwners'] for f in o['fragments']}
    physical={(o['dimension'],*p) for case in normalized for o in case['retainedOwners'] for p in o['runtimeMemberCells']}
    strict={(o['dimension'],*p['position']) for case in normalized for o in case['retainedOwners'] for p in o['memberPolicies'] if p['policy']=='original'}
    native_points={(case['retainedNative']['dimension'],*p) for case in normalized for p in case['retainedNative']['mustRemainOriginalCells']}
    physical|=native_points;strict|=native_points
    projected={}
    for case in normalized:
        for owner in case['retainedOwners']:
            for policy in owner['memberPolicies']:
                if policy['policy']=='original':continue
                key=(owner['dimension'],*policy['position'])
                m.require(key not in strict,'A projected member overlaps a strict preserved member')
                bridge=projection_bridge(m,policy)
                if key in projected:m.require(projected[key]==bridge,'Conflicting finite projected carrier policies')
                projected[key]=bridge
    m.require(expected|physical==set(snapshots) and len(snapshots)<=32768,'Certificate points must equal the complete finite visual/member union')
    points=[]
    for key,saved in sorted(snapshots.items()):
        actual=point(m,db,key[0],key[1:]);m.require(actual==saved,'Actual finite point differs from certificate: '+str(key))
        m.require(actual['queue'] in ([],[{'processed':0}],[{'processed':1}]),'Unknown native queue state')
        demands={'fragments':[row for row in actual['fragments'] if (key[0],row['uuid']) not in owner_ids]}
        for table in ('substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots'):demands[table]=actual[table]
        demanded=any(demands.values())
        m.require(not (key in strict and demanded),'Independent demand at an original physical member; no graph expansion')
        m.require(not demanded or actual['queue'],'Independent visual work has no existing queue row')
        m.require(demanded or actual['queue']!=[{'processed':1}],'Processed native queue without remaining background may never be pruned/reset')
        m.require(key not in projected or demanded,'A projected member needs actual independent background work')
        points.append({'dimension':key[0],'position':list(key[1:]),'queue':actual['queue'],'remainingDemands':demands,
                       'bridge':actual['bridge_cells'],'bridgeWrite':projected.get(key),
                       'pruneQueueAndBridge':not demanded and bool(actual['queue'])})
    return {'schemaVersion':1,'status':'GENERIC_FINITE_RETENTION_PREVIEW','kind':'GENERIC_FINITE_NATIVE_RETENTION',
            'plan':str(plan),'sourceBinding':certificate['sourceBinding'],'semanticBindings':n.snapshot_bindings(m,db),
            'scope':reference,'previousRetention':m.meta(db,'nativeLocalRetention'),'priorRetainedComponents':m.meta(db,'nativeRetainedComponents'),
            'cases':normalized,'pointPlan':points,'beforeCounts':n.counts(db),'sourceFiles':source_files(m),
            'selectedOwnersRemoved':len(owner_ids),'fragmentRowsRemoved':sum(len(o['fragments']) for case in normalized for o in case['retainedOwners']),
            'queueRowsPruned':sum(p['pruneQueueAndBridge'] for p in points),'bridgeRowsPruned':sum(p['pruneQueueAndBridge'] and bool(p['bridge']) for p in points),
            'bridgeRowsAdded':sum(bool(p['bridgeWrite']) and not p['bridge'] for p in points),
            'finalCoverageRequired':{'nativeFunctions':True,'carrierAudit':True,'affectedGameplayQa':True} if projected else None,
            'consumerSourceTransition':m.meta(db,'nativeResumeSourceTransition'),
            'evidence':{'failedRun':certificate['failedRun'],'failedSource':archives['migrate.py']},'worldReads':0,'mapWrites':0}


def retain(a,m):
    plan=m.checked(a.plan);db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+('?mode=rw' if a.execute else '?mode=ro'),uri=True)
    try:
        db.execute('PRAGMA cache_size=-2048')
        if a.execute:db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE' if a.execute else 'BEGIN')
        if a.execute:
            m.require(a.expected_preview_sha256 and m.sha(a.preview)==a.expected_preview_sha256,'Execute only the exact reviewed finite certificate preview')
            previous=m.meta(db,'nativeLocalRetention')
            if bound(m,previous)['preview']['sha256']==a.expected_preview_sha256:n.validate_resume(m,db,plan);db.rollback();return
        preview=certificate_preview(a,m,db)
        if not a.execute:immutable_json(m,a.preview,preview);return
        m.require(preview==m.read(a.preview),'Finite certificate state changed before atomic execution')
        n.apply_scope_delta(m,db,preview);after=n.counts(db);before=preview['beforeCounts']
        expected={**before,'selected_objects':before['selected_objects']-preview['selectedOwnersRemoved'],
                  'fragments':before['fragments']-preview['fragmentRowsRemoved'],'cell_queue':before['cell_queue']-preview['queueRowsPruned'],
                  'bridge_cells':before['bridge_cells']-preview['bridgeRowsPruned']+preview['bridgeRowsAdded']}
        m.require(after==expected and n.snapshot_bindings(m,db)==preview['semanticBindings'],'Finite retention changed unrelated native work/input evidence')
        retained=[case['retainedNative'] for case in preview['cases']]
        receipt={key:preview[key] for key in ('sourceBinding','semanticBindings','scope','previousRetention','sourceFiles','evidence','consumerSourceTransition')}
        receipt.update(schemaVersion=1,status='PASS',kind='EXPLICIT_FINITE_NATIVE_RETENTION',preview=n.ref(m,a.preview),
                       beforeCounts=before,afterCounts=after,retainedNative=retained,
                       selectedOwnersRetained=[o['uuid'] for case in preview['cases'] for o in case['retainedOwners']],
                       finalCoverageRequired=preview['finalCoverageRequired'],
                       objectsClassificationUnchanged=True,processedQueueRowsReset=0,worldReads=0,mapWrites=0)
        output=plan/'full/native-retention-receipts'/(a.expected_preview_sha256+'.json')
        if output.exists():m.require(m.read(output)==receipt,'Interrupted finite certificate receipt changed')
        else:m.atomic_json(output,receipt)
        reference=n.ref(m,output);m.meta(db,'nativeLocalRetention',reference)
        m.meta(db,'nativeRetainedComponents',preview['priorRetainedComponents']+retained)
        cursor=m.meta(db,'nativeComposeCursor');m.meta(db,'nativeComposeCursor',{**cursor,'retention':reference});db.commit()
    finally:db.close()


def retraction_proof_files(m,document):
    m.require(document.get('schemaVersion')==1 and document.get('status')=='PASS' and document.get('kind')=='FINITE_NATIVE_RETRACTION_CERTIFICATE_V1' and document.get('ownerGraphTraversal') is False,'Unknown native retraction certificate')
    natives=[document['native']]+[row['native'] for row in document['retractedNatives']]
    m.require(0<len(document['retractedNatives'])<=8 and len(document['owners'])<=8,'Retraction needs an explicit bounded activated-native list')
    for native in natives:
        proof=bound(m,native['sourceProof'])
        m.require(proof.get('status')=='PASS' and proof.get('sourceBinding')==document['sourceBinding'],'Native retraction source proof changed')
        rows=[row for row in proof.get('nativeComponents',[]) if row['uuid']==native['uuid']]
        m.require(len(rows)==1 and all(rows[0][key]==native[key] for key in ('pose','poseDataSha256','root','actualRootState','runtimeMemberCells')),'Exact original native whole/member proof missing')
    for retracted in document['retractedNatives']:
        proof=bound(m,retracted['sourceProof']);rows=[row for row in proof.get('nativeComponents',[]) if row['uuid']==retracted['native']['uuid']]
        m.require(proof.get('status')=='PASS' and proof.get('sourceBinding')==document['sourceBinding'] and len(rows)==1 and all(rows[0][key]==retracted[key] for key in ('activatedRow','substrateFragments')),'Activated native/complete derived rows lack exact source proof')
    # Reuse the unchanged owner/runtime proof contract, including member modes.
    surrogate={**document,'kind':'FINITE_NATIVE_RETENTION_CERTIFICATE_V1','cases':[]}
    if document['owners']:
        surrogate['cases']=[{'native':document['native'],'owners':document['owners']}]
        proof_files(m,surrogate)
    guard=bound(m,document['retirementNativeGuard'])
    m.require(guard.get('status')=='PASS' and guard.get('sourceBinding')==document['sourceBinding'],'Completed-row native suppression proof missing/changed')


def retirement_native_demand(m,db,row,excluded):
    """Resolve actual recipe origins, including empty root visual slots."""
    state=row['sourceState'];binding=m.meta(db,'poseFragments')
    m.require(binding and m.sha(binding['path'])==binding['sha256'],'Native suppression guard uses changed exact export rows')
    exports=sqlite3.connect('file:'+Path(binding['path']).as_posix()+'?mode=ro',uri=True)
    try:
        saved=exports.execute('SELECT data FROM native WHERE state=?',(m.canonical(state),)).fetchone()
        native=m.json.loads(saved[0]) if saved else None
    finally:exports.close()
    roots=[{'uuid':uid,'pose':pose,'root':[x,y,z]} for uid,pose,x,y,z in m.native_roots_at(db,row['dimension'],tuple(row['position']),state,native)]
    m.require(sorted(roots,key=m.canonical)==sorted(row['nativeRoots'],key=m.canonical),'Actual native member/recipe identities changed at proposed completed-row retirement')
    result=[]
    for root in roots:
        token=(row['dimension'],root['uuid'])
        if token not in excluded and db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',token).fetchone():result.append({'dim':token[0],'uuid':token[1]})
    result=sorted(result,key=m.canonical)
    m.require(result==sorted(row['foreignActivated'],key=m.canonical),'Foreign activated native suppression demand changed')
    return result


def retraction_preview(a,m,db):
    plan=m.checked(a.plan);n.validate_resume(m,db,plan);active_binding(m,db)
    reference={'path':str(m.checked(a.certificate)),'sha256':a.certificate_sha256};document=bound(m,reference)
    retraction_proof_files(m,document)
    guard=bound(m,document['retirementNativeGuard']);guard_points={(row['dimension'],*row['position']):row for row in guard['points']}
    m.require(len(guard_points)==len(guard['points']),'Duplicate native retirement guard point')
    m.require(document['sourceBinding']==m.meta(db,'binding'),'Retraction belongs to another source')
    failure,archives,files=failed_evidence(m,db,document['failedRun']);require_source_files(m,db,files)
    log=m.checked(failure['log']).read_text(encoding='utf-8',errors='replace')
    m.require('Required native visual overhang meets a protected local object' in log and document['native']['uuid'] in log,'Retraction does not name its actual failed C component')
    classification=bound(m,m.meta(db,'classification'));snapshots={};owner_ids=set();retracted_ids=set();native_rows=[]
    def remember(saved):
        token=(saved['dimension'],*saved['position'])
        m.require(len(token)==4 and all(type(v)is int for v in token[1:]),'Retraction points require exact integer coordinates')
        if token in snapshots:m.require(snapshots[token]==saved,'Conflicting finite retraction snapshots')
        else:snapshots[token]=saved
    for saved in document['pointDemands']:remember(saved)
    expected_points=set()
    all_natives=[document['native']]+[entry['native'] for entry in document['retractedNatives']]
    identities={(row['dimension'],row['uuid']) for row in all_natives}
    m.require(len(identities)==len(all_natives) and not identities&{(row['dimension'],row['uuid']) for row in m.meta(db,'nativeRetainedComponents') or []},'Native identity already retained/duplicated')
    for index,native in enumerate(all_natives):
        token=(native['dimension'],native['uuid'])
        m.require(db.execute('SELECT pose,x,y,z,kind FROM objects WHERE dim=? AND uuid=?',token).fetchone()==(native['pose'],*native['root'],'substrate'),'Native retraction object/root changed')
        pose=db.execute('SELECT data FROM poses WHERE key=?',(native['pose'],)).fetchone()
        m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==native['poseDataSha256'],'Native retraction exact pose changed')
        m.require(0<len(native['runtimeMemberCells'])<=4096,'Native physical footprint must be explicit')
        activation=n.row_dict(db,'SELECT * FROM activated_native WHERE dim=? AND uuid=?',token)
        if index==0:m.require(not activation,'Pending C cannot already be activated')
        else:
            entry=document['retractedNatives'][index-1];retracted_ids.add(token)
            m.require(activation==[entry['activatedRow']] and entry['activatedRow']=={'dim':token[0],'uuid':token[1]},'Retract exactly one previously committed native activation')
            actual=n.row_dict(db,'SELECT * FROM substrate_fragments WHERE dim=? AND uuid=? ORDER BY x,y,z',token)
            m.require(actual==entry['substrateFragments'] and 0<len(actual)<=4096,'Complete activated native substrate rows changed')
            expected={(native['root'][0]+dx,native['root'][1]+dy,native['root'][2]+dz,dx,dy,dz) for dx,dy,dz in db.execute('SELECT dx,dy,dz FROM pose_cells WHERE pose=?',(native['pose'],))}
            m.require(expected=={(row['x'],row['y'],row['z'],row['dx'],row['dy'],row['dz']) for row in actual} and all(row['pose']==native['pose'] for row in actual),'An activated native did not transfer its complete exported footprint')
            for saved in entry['pointDemands']:remember(saved)
            expected_points.update((row['dim'],row['x'],row['y'],row['z']) for row in actual)
        native_rows.append({'dimension':native['dimension'],'uuid':native['uuid'],'pose':native['pose'],'root':native['root'],'mustRemainOriginalCells':native['runtimeMemberCells']})
        expected_points.update((native['dimension'],*at) for at in native['runtimeMemberCells'])
    owners=[]
    for owner in document['owners']:
        token=(document['native']['dimension'],owner['uuid']);m.require(token not in owner_ids,'Duplicate explicit source owner');owner_ids.add(token)
        m.require(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',token)==[owner['object']] and n.row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND uuid=?',token)==[owner['selected']] and owner['selected']['kind']=='static','Exact direct selected owner changed')
        m.require(owner['pose']==owner['object']['pose'] and owner['root']==[owner['object'][axis] for axis in ('x','y','z')],'Direct owner pose/root changed')
        pose=db.execute('SELECT data FROM poses WHERE key=?',(owner['pose'],)).fetchone()
        m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==owner['poseDataSha256'],'Direct owner pose data changed')
        fragments=n.row_dict(db,'SELECT x,y,z,pose,dx,dy,dz FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=? ORDER BY x,y,z',token)
        m.require(fragments==owner['fragments'] and 0<len(fragments)<=4096,'Complete direct owner fragments changed')
        for saved in owner['pointDemands']:remember(saved)
        policies=member_policies(m,owner,classification)
        owners.append({'dimension':token[0],'uuid':token[1],'object':owner['object'],'selected':owner['selected'],'fragments':fragments,'runtimeMemberCells':owner['runtimeMemberCells'],'memberPolicies':policies})
        expected_points.update((token[0],row['x'],row['y'],row['z']) for row in fragments)
        expected_points.update((token[0],*at) for at in owner['runtimeMemberCells'])
    for saved in document['nativePointDemands']:remember(saved)
    m.require(set(snapshots)==expected_points and len(snapshots)<=32768,'Retraction snapshots must equal the complete C/A/owner finite union')
    strict={(row['dimension'],*at) for row in native_rows for at in row['mustRemainOriginalCells']};projected={}
    for owner in owners:
        for policy in owner['memberPolicies']:
            token=(owner['dimension'],*policy['position'])
            if policy['policy']=='original':strict.add(token)
            else:
                bridge=projection_bridge(m,policy)
                if token in projected:m.require(projected[token]==bridge,'Inconsistent projected member carriers')
                projected[token]=bridge
    m.require(not strict&projected.keys(),'An original native/owner physical member cannot carry a projected background')
    points=[];guarded=set();removed_substrate=sum(len(row['substrateFragments']) for row in document['retractedNatives'])
    for token,saved in sorted(snapshots.items()):
        actual=point(m,db,token[0],token[1:]);m.require(actual==saved,'Retraction point changed: '+str(token))
        m.require(actual['queue'] in ([],[{'processed':0}],[{'processed':1}]),'Unknown retraction queue state')
        demands={'fragments':[row for row in actual['fragments'] if (token[0],row['uuid']) not in owner_ids],
                 'substrate_fragments':[row for row in actual['substrate_fragments'] if (token[0],row['uuid']) not in retracted_ids]}
        for table in ('native_visual_fragments','whole_cells','native_visual_roots'):demands[table]=actual[table]
        native_guard=None;demands['implicitNativeActivations']=[]
        if actual['queue']==[{'processed':1}] and not any(demands.values()):
            m.require(token in guard_points,'Completed-row retirement lacks exact native component suppression proof')
            native_guard=guard_points[token];guarded.add(token)
            demands['implicitNativeActivations']=retirement_native_demand(m,db,native_guard,retracted_ids)
        demanded=any(demands.values())
        m.require(token not in strict or not demanded,'Independent demand still touches an original C/A/D member; no graph expansion')
        m.require(token not in projected or demanded,'Projected source member lacks remaining background')
        m.require(not demanded or actual['queue'],'Remaining work has no existing queue row')
        points.append({'dimension':token[0],'position':list(token[1:]),'queue':actual['queue'],'remainingDemands':demands,'bridge':actual['bridge_cells'],
                       'nativeGuard':native_guard,'bridgeWrite':projected.get(token),'pruneQueueAndBridge':not demanded and bool(actual['queue'])})
    m.require(guarded==set(guard_points),'Native guard must bind exactly all proposed completed-row retirements')
    retired=[{'dimension':row['dimension'],'position':row['position'],'processed':row['queue'][0]['processed']} for row in points if row['pruneQueueAndBridge']]
    return {'schemaVersion':1,'status':'EXACT_NATIVE_RETRACTION_PREVIEW','kind':'FINITE_NATIVE_RETRACTION','plan':str(plan),
            'sourceBinding':document['sourceBinding'],'semanticBindings':n.snapshot_bindings(m,db),'scope':reference,
            'previousRetention':m.meta(db,'nativeLocalRetention'),'priorRetainedComponents':m.meta(db,'nativeRetainedComponents'),
            'sourceFiles':source_files(m),'cursorBefore':m.meta(db,'nativeComposeCursor'),
            'cases':[{'policy':'explicit_native_retraction','retainedNative':native_rows[0],'retainedAdditionalNative':native_rows[1:],'retainedOwners':owners}],
            'retractedNatives':document['retractedNatives'],'pointPlan':points,'beforeCounts':n.counts(db),
            'selectedOwnersRemoved':len(owners),'fragmentRowsRemoved':sum(len(o['fragments']) for o in owners),'activatedNativeRowsRemoved':len(retracted_ids),
            'substrateRowsRemoved':removed_substrate,'queueRowsPruned':len(retired),'retiredQueueRows':retired,
            'bridgeRowsPruned':sum(row['pruneQueueAndBridge'] and bool(row['bridge']) for row in points),
            'bridgeRowsAdded':sum(bool(row['bridgeWrite']) and not row['bridge'] for row in points),
            'consumerSourceTransition':m.meta(db,'nativeResumeSourceTransition'),
            'finalCoverageRequired':{'nativeFunctions':True,'carrierAudit':True,'affectedGameplayQa':True},
            'evidence':{'failedRun':document['failedRun'],'failedSource':archives['migrate.py']},'worldReads':0,'mapWrites':0}


def apply_retraction_delta(m,db,preview):
    for entry in preview['retractedNatives']:
        native=entry['native'];token=(native['dimension'],native['uuid'])
        m.require(n.row_dict(db,'SELECT * FROM activated_native WHERE dim=? AND uuid=?',token)==[entry['activatedRow']],'Activated row changed before retraction')
        m.require(n.row_dict(db,'SELECT * FROM substrate_fragments WHERE dim=? AND uuid=? ORDER BY x,y,z',token)==entry['substrateFragments'],'Native source rows changed before retraction')
        db.execute('DELETE FROM substrate_fragments WHERE dim=? AND uuid=?',token);db.execute('DELETE FROM activated_native WHERE dim=? AND uuid=?',token)
    for case in preview['cases']:
        for owner in case['retainedOwners']:
            token=(owner['dimension'],owner['uuid']);db.execute('DELETE FROM fragments WHERE dim=? AND uuid=?',token);db.execute('DELETE FROM selected_objects WHERE dim=? AND uuid=?',token)
    for row in preview['pointPlan']:
        args=(row['dimension'],*row['position']);current={}
        for table in ('fragments','substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots'):
            columns='uuid,pose' if table=='fragments' else '*';current[table]=n.row_dict(db,'SELECT '+columns+' FROM '+table+' WHERE dim=? AND x=? AND y=? AND z=?',args)
        current['implicitNativeActivations']=retirement_native_demand(m,db,row['nativeGuard'],{(entry['native']['dimension'],entry['native']['uuid']) for entry in preview['retractedNatives']}) if row['nativeGuard'] else []
        m.require(current==row['remainingDemands'] and n.row_dict(db,'SELECT processed FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args)==row['queue'],'Exact retraction point/cursor changed')
        m.require(row['pruneQueueAndBridge']==(not any(current.values()) and bool(row['queue'])),'Retire only exactly undemanded scoped queue rows')
        if row['pruneQueueAndBridge']:
            db.execute('DELETE FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args);db.execute('DELETE FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args)
        if row['bridgeWrite']:
            bridge=row['bridgeWrite'];db.execute('INSERT OR REPLACE INTO bridge_cells VALUES(?,?,?,?,?,?,NULL)',(*args,bridge['host_state'],bridge['retained_resident']))
    for case in preview['cases']:
        for owner in case['retainedOwners']:
            for policy in owner['memberPolicies']:
                if policy['policy']=='original':db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(owner['dimension'],*policy['position'],owner['uuid'],n.REASON))
        for native in [case['retainedNative']]+case['retainedAdditionalNative']:
            for at in native['mustRemainOriginalCells']:db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(native['dimension'],*at,native['uuid'],'native_legacy_member_retained_exact_proof'))


def retract(a,m):
    plan=m.checked(a.plan);db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+('?mode=rw' if a.execute else '?mode=ro'),uri=True)
    try:
        db.execute('PRAGMA cache_size=-2048')
        if a.execute:db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE' if a.execute else 'BEGIN')
        if a.execute:
            m.require(a.expected_preview_sha256 and m.sha(a.preview)==a.expected_preview_sha256,'Execute only the exact reviewed native retraction preview')
            current=bound(m,m.meta(db,'nativeLocalRetention'))
            if current['preview']['sha256']==a.expected_preview_sha256:n.validate_resume(m,db,plan);db.rollback();return
        preview=retraction_preview(a,m,db)
        if not a.execute:immutable_json(m,a.preview,preview);return
        m.require(preview==m.read(a.preview),'Native retraction snapshot changed after review')
        apply_retraction_delta(m,db,preview);after=n.counts(db);before=preview['beforeCounts']
        expected={**before,'selected_objects':before['selected_objects']-preview['selectedOwnersRemoved'],
                  'fragments':before['fragments']-preview['fragmentRowsRemoved'],'substrate_fragments':before['substrate_fragments']-preview['substrateRowsRemoved'],
                  'activated_native':before['activated_native']-preview['activatedNativeRowsRemoved'],'cell_queue':before['cell_queue']-preview['queueRowsPruned'],
                  'bridge_cells':before['bridge_cells']-preview['bridgeRowsPruned']+preview['bridgeRowsAdded']}
        m.require(after==expected and n.snapshot_bindings(m,db)==preview['semanticBindings'] and m.meta(db,'nativeComposeCursor')==preview['cursorBefore'],'Retraction changed unrelated state/cursor/bindings')
        retained=[native for case in preview['cases'] for native in [case['retainedNative']]+case['retainedAdditionalNative']]
        receipt={key:preview[key] for key in ('sourceBinding','semanticBindings','scope','previousRetention','sourceFiles','evidence','consumerSourceTransition','finalCoverageRequired')}
        receipt.update(schemaVersion=1,status='PASS',kind='EXPLICIT_FINITE_NATIVE_RETENTION',preview=n.ref(m,a.preview),beforeCounts=before,afterCounts=after,
                       retainedNative=retained,selectedOwnersRetained=[owner['uuid'] for case in preview['cases'] for owner in case['retainedOwners']],
                       removedActivatedNativeRows=[entry['activatedRow'] for entry in preview['retractedNatives']],removedSubstrateRows=[row for entry in preview['retractedNatives'] for row in entry['substrateFragments']],
                       retiredPendingQueueRows=[row for row in preview['retiredQueueRows'] if row['processed']==0],retiredCompletedQueueRows=[row for row in preview['retiredQueueRows'] if row['processed']==1],
                       cursorBefore=preview['cursorBefore'],cursorCountersUnchanged=True,objectsClassificationUnchanged=True,processedQueueRowsReset=0,worldReads=0,mapWrites=0)
        output=plan/'full/native-retention-receipts'/(a.expected_preview_sha256+'.json');immutable_json(m,output,receipt)
        reference=n.ref(m,output);m.meta(db,'nativeLocalRetention',reference);m.meta(db,'nativeRetainedComponents',preview['priorRetainedComponents']+retained)
        m.meta(db,'nativeComposeCursor',{**preview['cursorBefore'],'retention':reference});db.commit()
    finally:db.close()


def preserved_whole_proof_files(m,document):
    """A named preserved-native set and one confirmed whole, never a family rule."""
    m.require(document.get('schemaVersion')==1 and document.get('status')=='PASS' and document.get('kind')=='FINITE_PRESERVED_NATIVE_WHOLE_CERTIFICATE_V1' and document.get('ownerGraphTraversal') is False,'Unknown preserved-native whole certificate')
    natives=[document['native']]+document['additionalPreservedNatives']
    m.require(0<len(natives)<=16 and len(document['owners'])==1,'Preserved whole fallback requires one whole and a bounded explicit native set')
    identities={(row['dimension'],row['uuid']) for row in natives}
    m.require(len(identities)==len(natives),'Duplicate preserved native identity')
    proof=bound(m,document['sourceProof'])
    m.require(proof.get('status')=='PASS' and proof.get('sourceBinding')==document['sourceBinding'],'Preserved whole source proof changed')
    for native in natives:
        source=bound(m,native['sourceProof']);rows=[row for row in source.get('nativeComponents',[]) if row['uuid']==native['uuid']]
        keys=('dimension','object','pose','poseDataSha256','root','actualRootState','runtimeMemberCells')
        m.require(source.get('status')=='PASS' and source.get('sourceBinding')==document['sourceBinding'] and len(rows)==1 and all(rows[0][key]==native[key] for key in keys),'Exact preserved-native object/physical proof missing')
        obj=native['object']
        m.require(obj['kind']=='preserve' and obj['reason'] and (obj['dim'],obj['uuid'])==(native['dimension'],native['uuid']) and obj['pose']==native['pose'] and [obj[k] for k in ('x','y','z')]==native['root'],'Native identity must keep its exact original preserve classification/reason')
        m.require(0<len(native['runtimeMemberCells'])<=4096 and len({tuple(at) for at in native['runtimeMemberCells']})==len(native['runtimeMemberCells']),'Preserved physical members must be explicit and unique')
    owner=document['owners'][0]
    for name in ('sourceGeometryProof','classProof'):
        source=bound(m,owner[name]);rows=[row for row in source.get('owners',[]) if row['uuid']==owner['uuid']]
        keys=('dimension','object','selected','pose','poseDataSha256','root','runtimeMemberCells','wholeCells','fragments','memberPolicies','wholePreservation')
        m.require(source.get('status')=='PASS' and source.get('sourceBinding')==document['sourceBinding'] and len(rows)==1 and all(rows[0][key]==owner[key] for key in keys),'Confirmed whole original mechanism/roles proof changed')
    m.require(owner['object']['kind']==owner['selected']['kind']=='whole' and owner['fragments']==[] and 0<len(owner['wholeCells'])<=4096,'This fallback accepts one complete whole-role selection, without static fragments')
    m.require(owner['object']['dim']==owner['selected']['dim']==owner['dimension'] and owner['object']['uuid']==owner['selected']['uuid']==owner['uuid'] and owner['pose']==owner['object']['pose']==owner['selected']['pose'] and owner['root']==[owner['object'][key] for key in ('x','y','z')],'Whole source/selection identity is inconsistent')
    members={tuple(at) for at in owner['runtimeMemberCells']}
    m.require(0<len(members)==len(owner['runtimeMemberCells'])<=4096 and len(owner['memberPolicies'])==len(members) and {tuple(row['position']) for row in owner['memberPolicies']}==members and all(row['policy']=='original' for row in owner['memberPolicies']),'Every actual whole member must remain original')
    preservation=owner['wholePreservation'];manifest=bound(m,preservation['manifest'])
    entries=[row for row in manifest['entries'] if row['key']==preservation['assemblyKey']]
    matches=[row for row in manifest['poseMatches'] if row['assemblyKey']==preservation['assemblyKey'] and row['pose']==owner['pose'] and row['poseDataSha256']==owner['poseDataSha256']]
    m.require(manifest['sourceBinding']==document['sourceBinding'] and len(entries)==1 and len(matches)==1 and preservation['runtimePolicy']=='compiled_whole_or_local_original' and entries[0]['preservation']['runtimePolicy']==preservation['runtimePolicy'] and entries[0]['preservation'].get('noBackgroundMerge') is True,'Whole fallback needs its exact confirmed pose and original-whole policy')
    guard=bound(m,document['queueNativeGuard'])
    m.require(guard.get('schemaVersion')==1 and guard.get('status')=='PASS' and guard.get('kind')=='EXACT_PRESERVED_NATIVE_WHOLE_FALLBACK_QUEUE_GUARD' and guard.get('sourceBinding')==document['sourceBinding'] and guard.get('failedRun')==document['failedRun'] and guard.get('wholeOwnerUuid')==owner['uuid'] and not guard.get('blocked') and not guard.get('otherProtectedOrigins'),'Exact whole-role native suppression guard is missing or blocked')
    allowed={(row['object']['dim'],row['uuid']):row for row in guard['allowedPreservedNatives']}
    m.require(set(allowed)==identities and len(allowed)==len(guard['allowedPreservedNatives']) and all(all(allowed[(row['dimension'],row['uuid'])][key]==row[key] for key in ('object','pose','poseDataSha256','root')) for row in natives),'Queue guard must allow exactly the source-proved preserved identities')
    m.require(document['finalCoverageRequired']=={'nativeFunctions':True,'carrierAudit':True,'affectedGameplayQa':True},'Whole fallback does not grant final gameplay acceptance')


def preserved_whole_guard_point(m,db,row,exports,allowed):
    """Resolve unchanged native_roots_at, with only named preserved recipe roots."""
    dim=row['dimension'];at=tuple(row['position']);state=row['sourceState']
    saved=exports.execute('SELECT data FROM native WHERE state=?',(m.canonical(state),)).fetchone()
    native=m.json.loads(saved[0]) if saved else None
    exported={'exists':bool(saved),'storedDataSha256':m.hashlib.sha256(saved[0].encode()).hexdigest() if saved else None,'nativeVisualComplete':native.get('nativeVisualComplete',True) if native is not None else None}
    m.require(exported==row['nativeExport'],'Exact native export changed at whole-role prune')
    error=None
    try:
        roots=m.native_roots_at(db,dim,at,state,native)
    except RuntimeError as exc:
        m.require(str(exc).startswith('A consumed native helper belongs to a retained/protected local object:'),'Unexpected native recipe failure at whole fallback')
        error=str(exc);roots=None
    doc,overlays=m.legacy_native_catalog();rid=state['registryId'];recipe=None
    if not (native and native.get('nativeVisualComplete',True)):
        if rid.startswith('bloodborne_dw:static_architecture_'):recipe=int(rid.rsplit('_',1)[1])*128+int(state.get('properties',{}).get('part',0))
        elif rid in overlays:recipe=overlays[rid]
    m.require(recipe==row['legacyRecipeIndex'] and (doc['recipes'][recipe] if recipe is not None else None)==row['legacyRecipe'],'Whole guard actual legacy recipe changed')
    legacy={};resolved=[]
    if recipe is not None:
        for fragment in doc['recipes'][recipe]['fragments']:
            root=tuple(at[i]+fragment['origin'][i] for i in range(3));uid=m.synthetic_uuid(dim,root,fragment['pose'])
            obj=n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(dim,uid))
            m.require(len(obj)==1,'Whole guard native recipe has no exact saved object')
            obj=obj[0]
            if obj['kind']=='whole' and db.execute("SELECT 1 FROM selected_objects WHERE dim=? AND uuid=? AND kind='whole'",(dim,uid)).fetchone():continue
            if obj['kind']=='preserve':m.require((dim,uid) in allowed and obj==allowed[(dim,uid)]['object'],'Another protected native is outside the finite whole fallback')
            else:m.require(obj['kind']=='substrate','Native recipe names another retained/protected kind')
            resolved.append((uid,obj['pose'],obj['x'],obj['y'],obj['z']));legacy[(uid,obj['pose'],tuple(root))]={'legacyPoseKey':fragment['pose'],'member':fragment['member']}
        if roots is None:roots=resolved
        else:m.require(roots==resolved,'Original native resolver and finite recipe resolution disagree')
    m.require(roots is not None and error==row['nativeRootsAtError'] and bool(error)==row['sourceRecipeResolutionPassed'],'Whole guard preserved-origin exception changed')
    actual=[];foreign=[]
    for uid,pose,x,y,z in roots:
        obj=n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(dim,uid))[0]
        activated=n.row_dict(db,'SELECT * FROM activated_native WHERE dim=? AND uuid=?',(dim,uid))
        item={'uuid':uid,'pose':pose,'root':[x,y,z],'objectKind':obj['kind'],'reason':obj['reason'],'activatedNative':activated,'allowedPreservedNative':(dim,uid) in allowed}
        item.update(legacy.get((uid,pose,(x,y,z)),{}));actual.append(item)
        if activated:foreign.extend(activated)
    m.require(sorted(actual,key=m.canonical)==sorted(row['nativeRoots'],key=m.canonical) and sorted(foreign,key=m.canonical)==sorted(row['foreignActivated'],key=m.canonical),'Whole guard actual native identities/activations changed')
    m.require(not foreign and row['queuePruningSafe'] is True,'Foreign activated empty-root suppression work cannot be pruned')


def preserved_whole_preview(a,m,db):
    plan=m.checked(a.plan);n.validate_resume(m,db,plan);active_binding(m,db)
    reference={'path':str(m.checked(a.certificate)),'sha256':a.certificate_sha256};document=bound(m,reference)
    preserved_whole_proof_files(m,document)
    m.require(document['sourceBinding']==m.meta(db,'binding'),'Whole fallback belongs to another source')
    failure,archives,files=failed_evidence(m,db,document['failedRun']);require_source_files(m,db,files)
    log=m.checked(failure['log']).read_text(encoding='utf-8',errors='replace')
    m.require('A consumed native helper belongs to a retained/protected local object:' in log and document['native']['uuid'] in log,'Whole fallback must name its actual failed preserved native')
    m.require(m.meta(db,'nativeComposeCursor')['phase']=='NATIVE_TRANSFER','Whole fallback requires the existing native-transfer commit boundary')
    natives=[document['native']]+document['additionalPreservedNatives'];prior=m.meta(db,'nativeRetainedComponents') or []
    identities={(row['dimension'],row['uuid']) for row in natives}
    m.require(not identities&{(row['dimension'],row['uuid']) for row in prior},'Preserved native identity was already admitted')
    snapshots={};expected=set();native_rows=[]
    def remember(saved):
        token=(saved['dimension'],*saved['position'])
        m.require(len(token)==4 and all(type(v)is int for v in token[1:]),'Whole fallback points need exact integer coordinates')
        if token in snapshots:m.require(snapshots[token]==saved,'Conflicting whole fallback finite point')
        else:snapshots[token]=saved
    for saved in document['pointDemands']:remember(saved)
    for native in natives:
        token=(native['dimension'],native['uuid'])
        m.require(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',token)==[native['object']],'Preserved native kind/reason/object changed')
        pose=db.execute('SELECT data FROM poses WHERE key=?',(native['pose'],)).fetchone()
        m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==native['poseDataSha256'],'Preserved native pose changed')
        m.require(not db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',token).fetchone() and not db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?',token).fetchone(),'Preserved native has activation/selection work')
        native_rows.append({key:native[key] for key in ('dimension','uuid','object','pose','poseDataSha256','root')})
        native_rows[-1]['mustRemainOriginalCells']=native['runtimeMemberCells']
        expected.update((native['dimension'],*at) for at in native['runtimeMemberCells'])
    native_points={(row['dimension'],*at) for row in natives for at in row['runtimeMemberCells']}
    m.require({(row['dimension'],*row['position']) for row in document['nativePointDemands']}==native_points and len(document['nativePointDemands'])==len(native_points),'All named preserved physical members must be bound')
    for saved in document['nativePointDemands']:remember(saved)
    owner=document['owners'][0];token=(owner['dimension'],owner['uuid'])
    m.require(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',token)==[owner['object']] and n.row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND uuid=?',token)==[owner['selected']],'Exact confirmed whole source/selection changed')
    pose=db.execute('SELECT data FROM poses WHERE key=?',(owner['pose'],)).fetchone()
    m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==owner['poseDataSha256'],'Whole source pose bytes changed')
    roles=n.row_dict(db,'SELECT * FROM whole_cells WHERE dim=? AND uuid=? ORDER BY x,y,z',token)
    m.require(roles==owner['wholeCells'] and all(row['template']=='wo_'+owner['pose'] and row['dim']==token[0] and row['uuid']==token[1] and [row[axis]-row['d'+axis] for axis in ('x','y','z')]==owner['root'] for row in roles),'Whole fallback needs all exact template roles at the original root')
    m.require(not db.execute('SELECT 1 FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=?',token).fetchone() and not db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',token).fetchone(),'Confirmed whole has unexpected fragment/activation work')
    for saved in owner['pointDemands']:remember(saved)
    role_points={(token[0],row['x'],row['y'],row['z']) for row in roles}
    m.require({(row['dimension'],*row['position']) for row in owner['pointDemands']}==role_points and len(owner['pointDemands'])==len(role_points),'Whole point evidence must cover exactly every role')
    policies=member_policies(m,owner,bound(m,m.meta(db,'classification')))
    normalized={key:owner[key] for key in ('dimension','uuid','object','selected','pose','poseDataSha256','root','runtimeMemberCells','wholeCells','wholePreservation','fragments')}
    normalized['memberPolicies']=policies
    strict=native_points|{(token[0],*at) for at in owner['runtimeMemberCells']};expected|=strict|role_points
    m.require(set(snapshots)==expected and len(snapshots)<=32768,'Whole fallback must bind the complete finite native/member/role union')
    guard=bound(m,document['queueNativeGuard']);guard_points={(row['dimension'],*row['position']):row for row in guard['points']}
    m.require(len(guard_points)==len(guard['points']),'Duplicate whole native guard point')
    points=[];prunes=set()
    for key,saved in sorted(snapshots.items()):
        actual=point(m,db,key[0],key[1:]);m.require(actual==saved,'Whole fallback finite point changed: '+str(key))
        m.require(actual['queue'] in ([],[{'processed':0}]),'Whole fallback accepts only pending/absent rows; never completed retirement')
        demands={'fragments':actual['fragments'],'substrate_fragments':actual['substrate_fragments'],'native_visual_fragments':actual['native_visual_fragments'],
                 'whole_cells':[row for row in actual['whole_cells'] if (key[0],row['uuid'])!=token],'native_visual_roots':actual['native_visual_roots']}
        demanded=any(demands.values());m.require(key not in strict or not demanded,'Independent physical demand blocks preserved whole fallback')
        m.require(not demanded or actual['queue'],'Independent work must retain its queue row')
        prune=not demanded and bool(actual['queue'])
        if prune:prunes.add(key)
        points.append({'dimension':key[0],'position':list(key[1:]),'queue':actual['queue'],'remainingDemands':demands,'bridge':actual['bridge_cells'],'pruneQueueAndBridge':prune})
    m.require(set(guard_points)==prunes,'Native-origin proof must cover exactly every proposed pending prune')
    fragment_binding=m.meta(db,'poseFragments');m.require(fragment_binding==guard['fragmentBinding'] and m.sha(fragment_binding['path'])==fragment_binding['sha256'],'Whole guard exact native export binding changed')
    m.require(m.sha(guard['sourceRecipeResource']['path'])==guard['sourceRecipeResource']['sha256'],'Whole guard source recipe bytes changed')
    exports=sqlite3.connect('file:'+Path(fragment_binding['path']).as_posix()+'?mode=ro',uri=True)
    try:
        allowed={(row['dimension'],row['uuid']):row for row in natives}
        for key in sorted(prunes):
            row=guard_points[key];m.require(row['queue']==snapshots[key]['queue'],'Whole native guard queue changed')
            preserved_whole_guard_point(m,db,row,exports,allowed)
    finally:exports.close()
    return {'schemaVersion':1,'status':'EXACT_PRESERVED_NATIVE_WHOLE_PREVIEW','kind':'FINITE_PRESERVED_NATIVE_WHOLE_RETENTION','plan':str(plan),
            'sourceBinding':document['sourceBinding'],'semanticBindings':n.snapshot_bindings(m,db),'scope':reference,'previousRetention':m.meta(db,'nativeLocalRetention'),
            'priorRetainedComponents':prior,'sourceFiles':source_files(m),'cursorBefore':m.meta(db,'nativeComposeCursor'),
            'cases':[{'policy':'whole_local_overlap','retainedNative':native_rows[0],'retainedAdditionalNative':native_rows[1:],'retainedOwners':[normalized]}],
            'pointPlan':points,'beforeCounts':n.counts(db),'selectedOwnersRemoved':1,'fragmentRowsRemoved':0,'wholeRowsRemoved':len(roles),
            'queueRowsPruned':len(prunes),'bridgeRowsPruned':sum(row['pruneQueueAndBridge'] and bool(row['bridge']) for row in points),
            'queueNativeGuard':document['queueNativeGuard'],'consumerSourceTransition':m.meta(db,'nativeResumeSourceTransition'),
            'finalCoverageRequired':document['finalCoverageRequired'],'evidence':{'failedRun':document['failedRun'],'failedSource':archives['migrate.py']},'worldReads':0,'mapWrites':0}


def apply_preserved_whole_delta(m,db,preview):
    for case in preview['cases']:
        for owner in case['retainedOwners']:
            token=(owner['dimension'],owner['uuid'])
            m.require(n.row_dict(db,'SELECT * FROM whole_cells WHERE dim=? AND uuid=? ORDER BY x,y,z',token)==owner['wholeCells'],'Complete whole roles changed before exact fallback')
            db.execute('DELETE FROM whole_cells WHERE dim=? AND uuid=?',token)
    n.apply_scope_delta(m,db,preview)
    for case in preview['cases']:
        natives=[case['retainedNative']]+case['retainedAdditionalNative']
        for native in natives[1:]:
            for at in native['mustRemainOriginalCells']:
                args=(native['dimension'],*at)
                m.require(not db.execute('SELECT 1 FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=?',args).fetchone(),'Preserved additional native physical member still has work')
                db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(*args,native['uuid'],'native_legacy_member_retained_exact_proof'))
        for owner in case['retainedOwners']:
            m.issue(db,'whole_local_overlap',owner['dimension'],tuple(owner['root']),{'uuid':owner['uuid'],'pose':owner['pose'],'policy':'whole_local_overlap',
                    'reason':'Preserve this confirmed whole in its original mechanism at the exact preserved-native physical overlap',
                    'nativeComponents':[{'uuid':row['uuid'],'pose':row['pose'],'root':row['root']} for row in natives],
                    'wholePreservation':owner['wholePreservation'],'scope':preview['scope'],'queueNativeGuard':preview['queueNativeGuard']})


def validate_preserved_whole_state(m,db,document,preview):
    natives=[document['native']]+document['additionalPreservedNatives'];case=preview['cases'][0]
    actual=[case['retainedNative']]+case['retainedAdditionalNative']
    expected=[{**{key:row[key] for key in ('dimension','uuid','object','pose','poseDataSha256','root')},'mustRemainOriginalCells':row['runtimeMemberCells']} for row in natives]
    m.require(len(preview['cases'])==1 and actual==expected,'Committed preserved native set/physical members changed')
    for native in natives:
        m.require(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(native['dimension'],native['uuid']))==[native['object']],'Preserved native original kind/reason/pose changed after fallback')
        pose=db.execute('SELECT data FROM poses WHERE key=?',(native['pose'],)).fetchone()
        m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==native['poseDataSha256'],'Preserved native pose bytes changed after fallback')
    m.require(len(case['retainedOwners'])==1,'Committed whole fallback owner set changed')
    owner=document['owners'][0];saved=case['retainedOwners'][0]
    m.require(all(saved[key]==owner[key] for key in ('dimension','uuid','object','selected','pose','poseDataSha256','root','runtimeMemberCells','wholeCells','wholePreservation','fragments','memberPolicies')),'Committed whole original identity/policies changed')
    token=(owner['dimension'],owner['uuid'])
    m.require(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',token)==[owner['object']] and not db.execute('SELECT 1 FROM whole_cells WHERE dim=? AND uuid=?',token).fetchone(),'Whole original object changed or compiled roles reentered')
    pose=db.execute('SELECT data FROM poses WHERE key=?',(owner['pose'],)).fetchone()
    m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==owner['poseDataSha256'],'Whole original pose bytes changed after fallback')


def retain_preserved_whole(a,m):
    plan=m.checked(a.plan);db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+('?mode=rw' if a.execute else '?mode=ro'),uri=True)
    try:
        db.execute('PRAGMA cache_size=-2048')
        if a.execute:db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE' if a.execute else 'BEGIN')
        if a.execute:
            m.require(a.expected_preview_sha256 and m.sha(a.preview)==a.expected_preview_sha256,'Execute only the exact reviewed preserved-whole preview')
            current=bound(m,m.meta(db,'nativeLocalRetention'))
            if current['preview']['sha256']==a.expected_preview_sha256:n.validate_resume(m,db,plan);db.rollback();return
        preview=preserved_whole_preview(a,m,db)
        if not a.execute:immutable_json(m,a.preview,preview);return
        m.require(preview==m.read(a.preview),'Preserved whole finite snapshot changed after review')
        apply_preserved_whole_delta(m,db,preview);after=n.counts(db);before=preview['beforeCounts']
        expected={**before,'selected_objects':before['selected_objects']-1,'whole_cells':before['whole_cells']-preview['wholeRowsRemoved'],
                  'cell_queue':before['cell_queue']-preview['queueRowsPruned'],'bridge_cells':before['bridge_cells']-preview['bridgeRowsPruned']}
        m.require(after==expected and n.snapshot_bindings(m,db)==preview['semanticBindings'] and m.meta(db,'nativeComposeCursor')==preview['cursorBefore'],'Whole fallback changed unrelated state/bindings/cursor')
        retained=[row for case in preview['cases'] for row in [case['retainedNative']]+case['retainedAdditionalNative']]
        receipt={key:preview[key] for key in ('sourceBinding','semanticBindings','scope','previousRetention','sourceFiles','evidence','consumerSourceTransition','finalCoverageRequired')}
        receipt.update(schemaVersion=1,status='PASS',kind='EXPLICIT_FINITE_NATIVE_RETENTION',preview=n.ref(m,a.preview),beforeCounts=before,afterCounts=after,
                       retainedNative=retained,selectedOwnersRetained=[owner['uuid'] for case in preview['cases'] for owner in case['retainedOwners']],
                       wholeRoleRowsRemoved=preview['wholeRowsRemoved'],cursorBefore=preview['cursorBefore'],cursorCountersUnchanged=True,
                       objectsClassificationUnchanged=True,processedQueueRowsReset=0,completedQueueRowsRetired=0,worldReads=0,mapWrites=0)
        output=plan/'full/native-retention-receipts'/(a.expected_preview_sha256+'.json');immutable_json(m,output,receipt)
        reference=n.ref(m,output);m.meta(db,'nativeLocalRetention',reference);m.meta(db,'nativeRetainedComponents',preview['priorRetainedComponents']+retained)
        m.meta(db,'nativeComposeCursor',{**preview['cursorBefore'],'retention':reference});db.commit()
    finally:db.close()


def visual_correction_proof(m,document):
    """Release one source-proved visual reservation, never a real member."""
    m.require(document.get('schemaVersion')==1 and document.get('status')=='PASS_SOURCE_CORRECTION_ONLY' and document.get('kind')=='FINITE_NATIVE_VISUAL_PROTECTION_CORRECTION_V1' and document.get('ownerGraphTraversal') is False,'Unknown native visual-protection correction')
    m.require(document.get('itemPickDropGapUnresolved') is True and document.get('gameplayPass') is False,'Source correction must not claim legacy item/gameplay acceptance')
    source=bound(m,document['sourceProof'])
    m.require(source.get('status')=='PASS_SOURCE_CORRECTION_ONLY' and source.get('sourceBinding')==document['sourceBinding'] and all(source[key]==document[key] for key in ('native','retainedWhole','protectionRow','pointSnapshot')),'Exact correction source facts changed')
    native=document['native'];whole=document['retainedWhole'];obj=whole['object'];row=document['protectionRow']
    m.require(obj['kind']=='whole' and whole['pose']==obj['pose'] and whole['root']==[obj[key] for key in ('x','y','z')],'Only the proved original whole classification may release a visual reservation')
    m.require(set(row)=={'dim','x','y','z','uuid','reason'} and row['reason']=='whole_local_overlap' and row['uuid']==obj['uuid'] and row['dim']==obj['dim']==native['dimension'],'Release only this exact whole-local visual protection row')
    at=[row[key] for key in ('x','y','z')];members=whole['runtimeMemberCells']
    m.require(0<len(members)<=4096 and len({tuple(p) for p in members})==len(members) and at!=whole['root'] and at not in members,'A root or physical/native helper protection cannot be released')
    m.require(len(whole['physicalMembers'])==len(members) and {tuple(p['position']) for p in whole['physicalMembers']}=={tuple(p) for p in members},'Every actual original whole member requires complete source proof')
    snapshot=document['pointSnapshot'];m.require(snapshot['dimension']==row['dim'] and snapshot['position']==at and snapshot['protected_cells']==[row] and not snapshot['bridge_cells'] and not snapshot['placements'],'Unknown/extra protected owners or a fabricated carrier cannot use a one-row correction')
    recipe=bound(m,source['sourceRecipeProof'])
    m.require(recipe['sourceBinding']==document['sourceBinding'] and recipe['statue']['object']==obj and recipe['statue']['poseDataSha256']==whole['poseDataSha256'] and recipe['statue']['runtimeMemberCells']==members and recipe['statue']['members']==whole['physicalMembers'],'Native whole physical/recipe source facts changed')
    m.require(recipe['actualTarget']['position']==at and recipe['actualTarget']['sourceBE'].get('exists') is False and not recipe['targetStatueRecipeOrigins'],'A foreign/root/unknown-BE target cannot be treated as a stale visual reservation')
    semantics=bound(m,document['nativeWholeSemantics'])
    m.require(semantics.get('status')=='PASS_SOURCE_REVIEW_NOT_EXECUTION' and semantics.get('sourceBinding')==document['sourceBinding'] and semantics.get('statueUuid')==obj['uuid'] and semantics.get('pose')==whole['pose'] and sorted(semantics['runtimeMemberCells'])==sorted(members) and semantics.get('wholeModelAndMemberLifecyclePreserved') is True,'Exact original native whole model/member lifecycle proof is required')
    m.require(semantics.get('itemPickDropGapUnresolved') is True and semantics.get('gameplayPass') is False,'Legacy pick/item/drop acceptance remains explicitly unresolved')
    history=bound(m,document['historicalStrictOriginalCheck'])
    m.require(history.get('sourceBinding')==document['sourceBinding'] and history.get('strictOriginalTargetMatches')==[] and history.get('targetPosition')==at,'Historical original-member evidence is not an exact negative check')
    return source,semantics,history


def correction_strict_history(m,db,dim,at,wanted_ancestor):
    current=m.meta(db,'nativeLocalRetention');seen=set();ancestor=False
    while current:
        m.require(current['sha256'] not in seen,'Retention history cycle in visual correction');seen.add(current['sha256'])
        ancestor|=current==wanted_ancestor
        receipt=bound(m,current);preview=bound(m,receipt['preview'])
        m.require(receipt.get('status')=='PASS' and receipt.get('kind')=='EXPLICIT_FINITE_NATIVE_RETENTION' and receipt['sourceBinding']==preview['sourceBinding']==m.meta(db,'binding'),'Strict-original history/source changed')
        for case in preview['cases']:
            for native in [case['retainedNative']]+case.get('retainedAdditionalNative',[]):
                m.require(not(native['dimension']==dim and at in native['mustRemainOriginalCells']),'Historical strict original native member cannot lose protection')
            for owner in case['retainedOwners']:
                policies={tuple(row['position']):row['policy'] for row in owner.get('memberPolicies',[])}
                m.require(not(owner['dimension']==dim and at in owner['runtimeMemberCells'] and policies.get(tuple(at),'original')=='original'),'Historical strict original owner member cannot lose protection')
        current=receipt.get('previousRetention')
    m.require(ancestor,'Correction source history head is not an immutable current ancestor')


def check_correction_whole(m,db,document,finalized=False):
    whole=document['retainedWhole'];obj=whole['object'];token=(obj['dim'],obj['uuid'])
    m.require(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',token)==[obj],'Corrected original whole kind/reason/root changed')
    pose=db.execute('SELECT data FROM poses WHERE key=?',(whole['pose'],)).fetchone()
    m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==whole['poseDataSha256'],'Original whole exact pose changed')
    classification=bound(m,m.meta(db,'classification'));protected_ids=set(classification.get('protectedRegistryIds',[]))
    m.require(m.json.loads(pose[0])['registryId'] not in protected_ids,'A v12/protected original whole cannot use this correction')
    recipe=bound(m,bound(m,document['sourceProof'])['sourceRecipeProof'])
    m.require(recipe['actualTarget']['sourceState']['registryId'] not in protected_ids,'A protected/v12 host cannot be released for native replacement')
    for table in ('selected_objects','whole_cells','fragments','activated_native','cleanup_owners'):
        m.require(not db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND uuid=? LIMIT 1',token).fetchone(),'Corrected original whole acquired conversion/cleanup work: '+table)
    for member in whole['physicalMembers']:
        at=member['position'];args=(obj['dim'],*at);saved=member['pointDemand']
        m.require(saved['dimension']==obj['dim'] and saved['position']==at and member['sourceBE'].get('exists') is False,'Whole physical source point must keep its original no-BE mechanism')
        current=point(m,db,obj['dim'],at)
        for table in ('queue','fragments','substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots','bridge_cells','placements'):
            m.require(not saved[table] and not current[table],'Original whole physical member has replacement work: '+str((args,table)))
        own=[row for row in saved['protected_cells'] if row['uuid']==obj['uuid']]
        m.require(len(own)==1 and own[0] in current['protected_cells'],'Original whole physical protection is missing')
    row=document['protectionRow'];at=[row[key] for key in ('x','y','z')]
    _,_,history=visual_correction_proof(m,document)
    correction_strict_history(m,db,obj['dim'],at,history['latestCommittedReceipt'])
    if finalized:
        placements=n.row_dict(db,'SELECT * FROM placements WHERE dim=? AND x=? AND y=? AND z=?',(row['dim'],*at))
        m.require(len(placements)==1 and type(placements[0]['type_index']) is int and placements[0]['type_index']>=0,'Final correction target requires its actual compiled ordinary placement')
        m.require(placements[0]['source_state']==m.canonical(recipe['actualTarget']['sourceState']),'Final target lost its exact original source-state binding')
        m.require(not db.execute('SELECT 1 FROM whole_cells WHERE dim=? AND x=? AND y=? AND z=?',(row['dim'],*at)).fetchone() and not db.execute('SELECT 1 FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',(row['dim'],*at)).fetchone(),'Visual-only native target must not become a whole role or BE wrapper')


def validate_visual_protection_corrections(m,db,finalized=False,source_files_validator=None):
    check_sources=source_files_validator or (lambda files:require_source_files(m,db,files))
    reference=m.meta(db,'nativeVisualProtectionCorrection');seen=set()
    while reference:
        m.require(reference['sha256'] not in seen,'Visual correction history cycle');seen.add(reference['sha256'])
        receipt=bound(m,reference);document=bound(m,receipt['certificate'])
        m.require(receipt.get('status')=='PASS_SOURCE_CORRECTION_ONLY' and receipt.get('kind')=='EXACT_NATIVE_VISUAL_PROTECTION_CORRECTION' and receipt['sourceBinding']==document['sourceBinding']==m.meta(db,'binding'),'Visual correction receipt/source changed')
        check_sources(receipt['sourceFiles']);visual_correction_proof(m,document);check_correction_whole(m,db,document,finalized)
        row=document['protectionRow']
        m.require(not db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?',(row['dim'],row['x'],row['y'],row['z'])).fetchone(),'Corrected visual point acquired protection again')
        reference=receipt['previousCorrection']


def visual_correction_preview(a,m,db):
    plan=m.checked(a.plan);n.validate_resume(m,db,plan);active_binding(m,db)
    reference={'path':str(m.checked(a.certificate)),'sha256':a.certificate_sha256};document=bound(m,reference)
    _,_,history=visual_correction_proof(m,document)
    m.require(document['sourceBinding']==m.meta(db,'binding'),'Visual correction belongs to another immutable source')
    failure,archives,files=failed_evidence(m,db,document['failedRun']);require_source_files(m,db,files)
    log=m.checked(failure['log']).read_text(encoding='utf-8',errors='replace');native=document['native']
    m.require('Required native visual overhang meets a protected local object' in log and native['uuid'] in log,'Correction must name the actual failed native visual')
    m.require(m.meta(db,'nativeComposeCursor')['phase']=='NATIVE_TRANSFER','Correct only the current native-transfer commit boundary')
    m.require(db.execute('SELECT pose,x,y,z,kind FROM objects WHERE dim=? AND uuid=?',(native['dimension'],native['uuid'])).fetchone()==(native['pose'],*native['root'],'substrate'),'Actual failed native source/root changed')
    pose=db.execute('SELECT data FROM poses WHERE key=?',(native['pose'],)).fetchone()
    m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==native['poseDataSha256'],'Actual failed native exact pose changed')
    m.require(not db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',(native['dimension'],native['uuid'])).fetchone(),'This correction cannot retract a native activation')
    m.require(m.meta(db,'nativeLocalRetention')==history['latestCommittedReceipt'],'Review correction against the exact current committed history head')
    row=document['protectionRow'];at=[row[key] for key in ('x','y','z')]
    m.require(point(m,db,row['dim'],at)==document['pointSnapshot'],'Exact visual-only point/protection changed before correction')
    check_correction_whole(m,db,document)
    return {'schemaVersion':1,'status':'EXACT_NATIVE_VISUAL_PROTECTION_CORRECTION_PREVIEW','sourceBinding':document['sourceBinding'],
            'semanticBindings':n.snapshot_bindings(m,db),'sourceFiles':source_files(m),'certificate':reference,'protectionRow':row,
            'beforeCounts':n.counts(db),'cursorBefore':m.meta(db,'nativeComposeCursor'),'retentionBefore':m.meta(db,'nativeLocalRetention'),
            'retainedComponentsBefore':m.meta(db,'nativeRetainedComponents'),'previousCorrection':m.meta(db,'nativeVisualProtectionCorrection'),
            'evidence':{'failedRun':document['failedRun'],'failedSource':archives['migrate.py']},'sourceOnly':True,'itemPickDropGapUnresolved':True,'gameplayPass':False,'mapWrites':0}


def correct_visual_protection(a,m):
    plan=m.checked(a.plan);db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+('?mode=rw' if a.execute else '?mode=ro'),uri=True)
    try:
        db.execute('PRAGMA cache_size=-2048')
        if a.execute:db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE' if a.execute else 'BEGIN')
        if a.execute:
            m.require(a.expected_preview_sha256 and m.sha(a.preview)==a.expected_preview_sha256,'Execute only the exact reviewed visual-protection correction preview')
            previous=m.meta(db,'nativeVisualProtectionCorrection')
            if previous and bound(m,previous)['preview']['sha256']==a.expected_preview_sha256:n.validate_resume(m,db,plan);db.rollback();return
        preview=visual_correction_preview(a,m,db)
        if not a.execute:immutable_json(m,a.preview,preview);return
        m.require(preview==m.read(a.preview),'Visual correction changed after reviewed preview')
        row=preview['protectionRow'];columns=('dim','x','y','z','uuid','reason')
        changed=db.execute('DELETE FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=? AND reason=?',tuple(row[key] for key in columns)).rowcount
        m.require(changed==1,'Release exactly one certified visual-only protection row')
        m.issue(db,'whole_visual_only_protection_correction',row['dim'],(row['x'],row['y'],row['z']),{'uuid':row['uuid'],'reason':'Remove only this stale whole visual reservation outside every actual native member; keep original whole root/member mechanism',
                'certificate':preview['certificate'],'sourceOnly':True,'itemPickDropGapUnresolved':True,'gameplayPass':False})
        m.require(n.counts(db)==preview['beforeCounts'] and n.snapshot_bindings(m,db)==preview['semanticBindings'] and m.meta(db,'nativeComposeCursor')==preview['cursorBefore'] and m.meta(db,'nativeLocalRetention')==preview['retentionBefore'] and m.meta(db,'nativeRetainedComponents')==preview['retainedComponentsBefore'],'One-row correction changed native work/cursor/input bindings')
        receipt={**preview,'status':'PASS_SOURCE_CORRECTION_ONLY','kind':'EXACT_NATIVE_VISUAL_PROTECTION_CORRECTION','preview':n.ref(m,a.preview),'afterCounts':n.counts(db),
                 'protectedRowsDeleted':1,'selectedRowsChanged':0,'nativeRowsChanged':0,'queueRowsChanged':0,'processedQueueRowsReset':0,'objectsClassificationChanged':False}
        output=plan/'full/native-visual-protection-corrections'/(a.expected_preview_sha256+'.json');immutable_json(m,output,receipt)
        m.meta(db,'nativeVisualProtectionCorrection',n.ref(m,output));db.commit()
    finally:db.close()


def whole_template_fallback_proof(m,document):
    """One confirmed selected whole returns to its complete original native pose."""
    m.require(document.get('schemaVersion')==1 and document.get('status')=='PASS' and document.get('kind')=='FINITE_NATIVE_WHOLE_TEMPLATE_FALLBACK_V1' and document.get('ownerGraphTraversal') is False and document.get('applyAuthorization') is False,'Unknown whole-template fallback certificate')
    source=bound(m,document['sourceProof'])
    m.require(source.get('status')=='PASS' and source.get('sourceBinding')==document['sourceBinding'] and source.get('failedRun')==document['failedRun'] and source.get('locationProof')==document['locationProof'] and source.get('queueNativeGuard')==document['queueNativeGuard'],'Whole-template source proof changed')
    for key in ('native','whole','keepNativeQueue','pointDemands'):
        m.require(source[key]==document[key],'Whole-template exact source field changed: '+key)
    native=document['native'];whole=document['whole']
    for row,kind in ((native,'substrate'),(whole,'whole')):
        obj=row['object'];members={tuple(at) for at in row['runtimeMemberCells']}
        m.require(obj['kind']==kind and obj['reason'] and (obj['dim'],obj['uuid'],obj['pose'])==(row['dimension'],row['uuid'],row['pose']) and [obj[k] for k in ('x','y','z')]==row['root'],'Whole fallback cannot relabel either source object')
        m.require(0<len(members)==len(row['runtimeMemberCells'])<=4096 and tuple(row['root']) in members and {tuple(p['position']) for p in row['physicalMembers']}==members and len(row['physicalMembers'])==len(members),'Exact original physical members are incomplete')
        for member in row['physicalMembers']:
            m.require(member['sourceBE'].get('exists') is False,'This native whole fallback requires its proved original no-BE mechanism')
            matches=member['matchingNativeFragments']
            m.require(len(matches)==1 and matches[0]['member'] is True and [member['position'][i]+matches[0]['origin'][i] for i in range(3)]==row['root'],'Whole fallback native recipe must resolve the complete exact root')
    m.require(native['uuid']!=whole['uuid'] and native['dimension']==whole['dimension'],'Distinct C and W identities are required')
    visual={tuple(at) for at in native['visualCells']};members={tuple(at) for at in whole['runtimeMemberCells']}
    m.require(0<len(visual)==len(native['visualCells'])<=4096 and not visual&members and not {tuple(at) for at in native['runtimeMemberCells']}&members,'Convertible native visual/physical cells cannot replace original whole members')
    m.require(whole['selected']=={key:whole['object'][key] for key in ('dim','uuid','pose','x','y','z','kind')} and whole['fragments']==[] and 0<len(whole['wholeCells'])<=4096,'Fallback needs exactly one complete selected whole without fragments')
    authority=whole['wholeAuthority'];classification=bound(m,authority['classification'])
    rows=[row for row in classification['poses'] if row.get('key')==whole['pose']]
    requests=[row for row in authority['exactWholeTemplateRequests'] if row['pose']==whole['pose']]
    m.require(len(rows)==1 and len(requests)==1 and rows[0]['route']=='whole' and rows[0]['reason']==whole['object']['reason'] and rows[0]['template']==requests[0]['key'],'Exact whole classification/template authority is missing')
    m.require(m.sha(authority['wholeTemplates']['path'])==authority['wholeTemplates']['sha256'],'Original whole template input changed')
    pose=bound(m,source['sourceFacts'])['whole']['poseData']
    ids=bound(m,authority['statueIds']);entries=[row for row in ids['entries'] if row['registryId']==pose['registryId']]
    m.require(len(entries)==1 and entries[0]['classification']=='whole_statue' and entries[0].get('protectedV12') is False and pose['registryId'] not in classification.get('protectedRegistryIds',[]),'Whole fallback requires exact non-v12 sculpture authority')
    facts=bound(m,source['sourceFacts']);m.require(facts['sourceBinding']==document['sourceBinding'],'Whole exact facts source binding changed')
    m.require(facts['native']['object']==native['object'] and facts['native']['physicalMembers']==native['physicalMembers'] and facts['whole']['object']==whole['object'] and facts['whole']['physicalMembers']==whole['physicalMembers'],'Whole fallback physical recipe facts changed')
    m.require(facts['whole']['ownPlanRows']['whole_cells']==whole['wholeCells'] and facts['whole']['ownPlanRows']['selected_objects']==[whole['selected']] and facts['native']['runtimeMemberCells']==native['runtimeMemberCells'] and facts['whole']['runtimeMemberCells']==whole['runtimeMemberCells'],'Whole role/member facts changed')
    m.require(all(row['dim']==whole['dimension'] and row['uuid']==whole['uuid'] and row['template']==requests[0]['key'] and [row[axis]-row['d'+axis] for axis in ('x','y','z')]==whole['root'] for row in whole['wholeCells']),'Every whole role must belong to the exact original template root')
    doc,_=m.legacy_native_catalog()
    for row in (native,whole):
        for member in row['physicalMembers']:
            state=member['sourceState'];rid=state['registryId']
            m.require(rid.startswith('bloodborne_dw:static_architecture_'),'This fallback requires the proved actual legacy native state')
            index=int(rid.rsplit('_',1)[1])*128+int(state['properties']['part'])
            m.require(index==member['recipeIndex'] and doc['recipes'][index]==member['recipe'],'Original whole/native physical recipe changed')
    m.require([[native['root'][i]+at[i] for i in range(3)] for at in facts['native']['visualOffsets']]==native['visualCells'],'Native complete visual footprint changed')
    semantics=bound(m,source['nativeWholeSemantics'])
    m.require(semantics.get('status')=='PASS_SOURCE_REVIEW_NOT_EXECUTION' and semantics.get('sourceBinding')==document['sourceBinding'] and semantics.get('statueUuid')==whole['uuid'] and semantics.get('pose')==whole['pose'] and semantics.get('runtimeMemberCells')==whole['runtimeMemberCells'] and semantics.get('wholeModelAndMemberLifecyclePreserved') is True,'Original whole model/member lifecycle proof is missing')
    m.require(semantics.get('itemPickDropGapUnresolved') is True and semantics.get('gameplayPass') is False,'Native whole lifecycle proof cannot claim item/gameplay acceptance')
    m.require(document['finalCoverageRequired']=={'nativeFunctions':True,'carrierAudit':True,'affectedGameplayQa':True} and document.get('itemPickDropGapUnresolved') is True and document.get('gameplayPass') is False,'Source fallback does not grant gameplay acceptance')
    guard=bound(m,document['queueNativeGuard'])
    m.require(guard.get('kind')=='EXACT_NATIVE_WHOLE_TEMPLATE_FALLBACK_QUEUE_GUARD' and guard.get('status')=='PASS' and guard.get('sourceBinding')==document['sourceBinding'] and guard.get('failedRun')==document['failedRun'] and guard.get('nativeUuid')==native['uuid'] and guard.get('wholeOwnerUuid')==whole['uuid'] and not guard.get('blocked'),'Whole pending-prune native proof is missing or blocked')
    return source,guard


def whole_template_fallback_state(m,db,document,finalized=False):
    """Recheck W only: ordinary C remains eligible for the existing conversion."""
    whole=document['whole'];token=(whole['dimension'],whole['uuid'])
    m.require(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',token)==[whole['object']],'Original whole fallback object changed')
    pose=db.execute('SELECT data FROM poses WHERE key=?',(whole['pose'],)).fetchone()
    m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==whole['poseDataSha256'],'Original whole fallback exact pose changed')
    for table in ('selected_objects','fragments','whole_cells','activated_native','cleanup_owners'):
        m.require(not db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND uuid=? LIMIT 1',token).fetchone(),'Original fallback whole acquired conversion/cleanup work: '+table)
    for at in whole['runtimeMemberCells']:
        current=point(m,db,token[0],at)
        m.require(all(not current[table] for table in ('queue','fragments','substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots','bridge_cells','placements')),'Original fallback whole physical member has replacement work')
        m.require(any(row['uuid']==token[1] and row['reason']=='whole_local_overlap' for row in current['protected_cells']),'Original fallback whole physical protection disappeared')
    if finalized:
        native=document['native']
        m.require(db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',(native['dimension'],native['uuid'])).fetchone(),'Ordinary C did not complete its intended native transfer')
        for at in native['visualCells']:
            rows=n.row_dict(db,'SELECT * FROM placements WHERE dim=? AND x=? AND y=? AND z=?',(native['dimension'],*at))
            m.require(len(rows)==1 and type(rows[0]['type_index']) is int and rows[0]['type_index']>=0,'Whole fallback must leave ordinary C visual cells actually compiled')
            m.require(not db.execute('SELECT 1 FROM whole_cells WHERE dim=? AND x=? AND y=? AND z=?',(native['dimension'],*at)).fetchone(),'A dedicated role reentered ordinary C visual cells')


def validate_whole_template_fallbacks(m,db,finalized=False,source_files_validator=None):
    check_sources=source_files_validator or (lambda files:require_source_files(m,db,files))
    reference=m.meta(db,'nativeWholeTemplateFallback');seen=set();owners=set()
    while reference:
        m.require(reference['sha256'] not in seen,'Whole-template fallback history cycle');seen.add(reference['sha256'])
        receipt=bound(m,reference);document=bound(m,receipt['certificate']);whole=document['whole'];token=(whole['dimension'],whole['uuid'])
        m.require(token not in owners,'Duplicate original whole fallback');owners.add(token)
        m.require(receipt.get('status')=='PASS_SOURCE_ONLY' and receipt.get('kind')=='EXACT_NATIVE_WHOLE_TEMPLATE_FALLBACK' and receipt['sourceBinding']==document['sourceBinding']==m.meta(db,'binding'),'Whole-template fallback receipt/source changed')
        check_sources(receipt['sourceFiles']);whole_template_fallback_proof(m,document);whole_template_fallback_state(m,db,document,finalized)
        reference=receipt['previousFallback']


def whole_template_fallback_preview(a,m,db):
    plan=m.checked(a.plan);n.validate_resume(m,db,plan);active_binding(m,db)
    reference={'path':str(m.checked(a.certificate)),'sha256':a.certificate_sha256};document=bound(m,reference)
    source,guard=whole_template_fallback_proof(m,document)
    m.require(document['sourceBinding']==m.meta(db,'binding'),'Whole-template fallback source changed')
    failure,archives,files=failed_evidence(m,db,document['failedRun']);require_source_files(m,db,files)
    log=m.checked(failure['log']).read_text(encoding='utf-8',errors='replace')
    m.require('Native overhang would overlap a dedicated whole-object role' in log,'Actual dedicated whole-role failure is required')
    location=bound(m,document['locationProof']);located=location['actualConflict'];native=document['native'];whole=document['whole']
    m.require(location['status']=='LOCATED_EXACT_CURRENT_PENDING_BATCH_WHOLE_ROLE_CONFLICT' and location['failedRun']==document['failedRun'] and location['sourceBinding']==document['sourceBinding'] and not location['reconstructionBlocker'] and located['replayReachedExactLine556AfterAllPriorGuards'] is True,'Actual whole-role failure was not located exactly')
    m.require(location['nativeComposeCursor']==m.meta(db,'nativeComposeCursor') and located['nativeObject']==native['object'] and located['wholeObject']==whole['object'] and located['targetWholeRole'] in whole['wholeCells'],'Whole-role reconstruction no longer matches this exact commit boundary')
    m.require(all(source_row['sha256']==files[name] for name,source_row in (('migrate',location['sourceAlgorithms']['migrate']),('nativeResume',location['sourceAlgorithms']['nativeResume']))),'Whole-role replay used another actual source generation')
    m.require(m.meta(db,'nativeComposeCursor')['phase']=='NATIVE_TRANSFER','Whole fallback requires current native transfer')
    for row in (native,whole):
        token=(row['dimension'],row['uuid'])
        m.require(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',token)==[row['object']],'Whole fallback exact source object changed')
        pose=db.execute('SELECT data FROM poses WHERE key=?',(row['pose'],)).fetchone()
        m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==row['poseDataSha256'],'Whole fallback source pose bytes changed')
        m.require(not db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',token).fetchone() and not db.execute('SELECT 1 FROM cleanup_owners WHERE dim=? AND uuid=?',token).fetchone(),'This fallback cannot retract activation/cleanup work')
    token=(whole['dimension'],whole['uuid']);role_rows=n.row_dict(db,'SELECT * FROM whole_cells WHERE dim=? AND uuid=? ORDER BY x,y,z',token)
    m.require(role_rows==whole['wholeCells'] and n.row_dict(db,'SELECT * FROM selected_objects WHERE dim=? AND uuid=?',token)==[whole['selected']] and not db.execute('SELECT 1 FROM fragments INDEXED BY fragment_owner WHERE dim=? AND uuid=?',token).fetchone(),'Complete selected W roles/fragments changed')
    m.require(not db.execute('SELECT 1 FROM selected_objects WHERE dim=? AND uuid=?',(native['dimension'],native['uuid'])).fetchone(),'Ordinary C must remain an unselected substrate')
    m.require(whole['wholeAuthority']['classification']==m.meta(db,'classification'),'Whole fallback classification authority changed')
    snapshots={(row['dimension'],*row['position']):row for row in document['pointDemands']}
    for row in (native,whole):
        for member in row['physicalMembers']:
            m.require(snapshots.get((row['dimension'],*member['position']))==member['pointDemand'],'Physical recipe and finite demand snapshots disagree')
    role_points={(token[0],row['x'],row['y'],row['z']) for row in role_rows};physical={(token[0],*at) for at in whole['runtimeMemberCells']}
    cvisual={(native['dimension'],*at) for at in native['visualCells']}
    expected=role_points|physical|cvisual|{(native['dimension'],*at) for at in native['runtimeMemberCells']}
    m.require(set(snapshots)==expected and len(snapshots)==len(document['pointDemands'])<=32768,'Bind the complete finite C/W role/member/visual union')
    keep={(row['dimension'],*row['position']) for row in document['keepNativeQueue']}
    seed=tuple(located['pendingCell']);root=(native['dimension'],*native['root'])
    m.require(keep=={seed,root} and len(keep)==len(document['keepNativeQueue']) and all(row['nativeUuid']==native['uuid'] and row['reason']=='exact_pending_native_activation' for row in document['keepNativeQueue']),'Keep exactly the actual pending native seed and root')
    m.require(seed in cvisual and snapshots[seed]['queue']==[{'processed':0}] and snapshots[root]['queue']==[{'processed':0}],'Native activation seed/root pending rows must survive')
    fragment_binding=m.meta(db,'poseFragments');m.require(fragment_binding==guard['fragmentBinding'] and m.sha(fragment_binding['path'])==fragment_binding['sha256'],'Whole pending-prune native export changed')
    m.require(m.sha(guard['sourceRecipeResource']['path'])==guard['sourceRecipeResource']['sha256'],'Whole native recipe resource changed')
    guard_points={(row['dimension'],*row['position']):row for row in guard['points']};prunes=set();points=[]
    m.require(len(guard_points)==len(guard['points']),'Duplicate whole pending-prune native guard')
    exports=sqlite3.connect('file:'+Path(fragment_binding['path']).as_posix()+'?mode=ro',uri=True)
    try:
        source_member={tuple(row['position']):row for row in native['physicalMembers']}
        seed_member=source_member.get(seed[1:]);m.require(seed_member is not None,'Native activation seed requires actual source member proof')
        state=seed_member['sourceState'];saved=exports.execute('SELECT data FROM native WHERE state=?',(m.canonical(state),)).fetchone()
        roots=m.native_roots_at(db,native['dimension'],seed[1:],state,m.json.loads(saved[0]) if saved else None)
        m.require((native['uuid'],native['pose'],*native['root']) in roots,'Actual kept seed no longer resolves ordinary C')
        for key,snapshot in sorted(snapshots.items()):
            actual=point(m,db,key[0],key[1:]);m.require(actual==snapshot,'Whole fallback finite demand point changed: '+str(key))
            m.require(actual['queue'] in ([],[{'processed':0}],[{'processed':1}]),'Unknown queue state')
            demands={'fragments':actual['fragments'],'substrate_fragments':actual['substrate_fragments'],'native_visual_fragments':actual['native_visual_fragments'],
                     'whole_cells':[row for row in actual['whole_cells'] if (key[0],row['uuid'])!=token],'native_visual_roots':actual['native_visual_roots']}
            demanded=any(demands.values()) or key in keep
            m.require(key not in physical or not demanded,'Independent physical demand blocks original W fallback')
            if key in cvisual:m.require(not actual['protected_cells'] and not demands['whole_cells'],'C visual clearance meets another protected/whole object')
            m.require(not demanded or actual['queue'] or key in cvisual,'Independent work must retain its existing queue')
            prune=not demanded and bool(actual['queue'])
            m.require(not prune or actual['queue']==[{'processed':0}],'Never retire completed queue work through whole fallback')
            if prune:
                prunes.add(key);row=guard_points.get(key);m.require(row and row['queue']==actual['queue'],'Every exact pending prune needs native-origin evidence')
                preserved_whole_guard_point(m,db,row,exports,{})
            points.append({'dimension':key[0],'position':list(key[1:]),'queue':actual['queue'],'remainingDemands':demands,'keepNativeActivation':key in keep,'pruneQueueAndBridge':prune,'bridge':actual['bridge_cells']})
    finally:exports.close()
    m.require(set(guard_points)==prunes,'Guard points must equal all and only proposed pending prunes')
    return {'schemaVersion':1,'status':'EXACT_NATIVE_WHOLE_TEMPLATE_FALLBACK_PREVIEW','sourceBinding':document['sourceBinding'],'semanticBindings':n.snapshot_bindings(m,db),
            'sourceFiles':source_files(m),'certificate':reference,'beforeCounts':n.counts(db),'cursorBefore':m.meta(db,'nativeComposeCursor'),
            'retentionBefore':m.meta(db,'nativeLocalRetention'),'retainedComponentsBefore':m.meta(db,'nativeRetainedComponents'),'previousFallback':m.meta(db,'nativeWholeTemplateFallback'),
            'wholeRowsRemoved':len(role_rows),'queueRowsPruned':len(prunes),'bridgeRowsPruned':sum(row['pruneQueueAndBridge'] and bool(row['bridge']) for row in points),
            'pointPlan':points,'evidence':{'failedRun':document['failedRun'],'failedSource':archives['migrate.py']},'finalCoverageRequired':document['finalCoverageRequired'],
            'itemPickDropGapUnresolved':True,'gameplayPass':False,'worldReads':0,'mapWrites':0}


def fallback_whole_template(a,m):
    plan=m.checked(a.plan);db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+('?mode=rw' if a.execute else '?mode=ro'),uri=True)
    try:
        db.execute('PRAGMA cache_size=-2048')
        if a.execute:db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE' if a.execute else 'BEGIN')
        if a.execute:
            m.require(a.expected_preview_sha256 and m.sha(a.preview)==a.expected_preview_sha256,'Execute only the exact reviewed whole-template fallback preview')
            previous=m.meta(db,'nativeWholeTemplateFallback')
            if previous and bound(m,previous)['preview']['sha256']==a.expected_preview_sha256:n.validate_resume(m,db,plan);db.rollback();return
        preview=whole_template_fallback_preview(a,m,db)
        if not a.execute:immutable_json(m,a.preview,preview);return
        m.require(preview==m.read(a.preview),'Whole fallback changed after reviewed preview')
        document=bound(m,preview['certificate']);whole=document['whole'];token=(whole['dimension'],whole['uuid'])
        m.require(db.execute('DELETE FROM whole_cells WHERE dim=? AND uuid=?',token).rowcount==preview['wholeRowsRemoved'],'Remove exactly the complete selected W roles')
        m.require(db.execute('DELETE FROM selected_objects WHERE dim=? AND uuid=?',token).rowcount==1,'Unselect exactly the confirmed whole W')
        for row in preview['pointPlan']:
            if row['pruneQueueAndBridge']:
                args=(row['dimension'],*row['position'])
                m.require(db.execute('DELETE FROM cell_queue WHERE dim=? AND x=? AND y=? AND z=? AND processed=0',args).rowcount==1,'Only exact pending whole-role work may be pruned')
                db.execute('DELETE FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',args)
        added=0
        for at in whole['runtimeMemberCells']:
            added+=db.execute('INSERT OR IGNORE INTO protected_cells VALUES(?,?,?,?,?,?)',(whole['dimension'],*at,whole['uuid'],'whole_local_overlap')).rowcount
        m.issue(db,'whole_local_overlap',whole['dimension'],tuple(whole['root']),{'uuid':whole['uuid'],'pose':whole['pose'],'reason':'Keep this complete confirmed statue in its original native mechanism; its visual template role intersects ordinary C without a physical member overlap',
                'certificate':preview['certificate'],'originalNativeMembers':whole['runtimeMemberCells'],'convertibleNative':document['native']['uuid'],'gameplayPass':False})
        before=preview['beforeCounts'];after=n.counts(db);expected={**before,'selected_objects':before['selected_objects']-1,'whole_cells':before['whole_cells']-preview['wholeRowsRemoved'],
            'cell_queue':before['cell_queue']-preview['queueRowsPruned'],'bridge_cells':before['bridge_cells']-preview['bridgeRowsPruned']}
        m.require(after==expected and n.snapshot_bindings(m,db)==preview['semanticBindings'] and m.meta(db,'nativeComposeCursor')==preview['cursorBefore'] and m.meta(db,'nativeLocalRetention')==preview['retentionBefore'] and m.meta(db,'nativeRetainedComponents')==preview['retainedComponentsBefore'],'Whole fallback changed unrelated native state/cursor/bindings')
        whole_template_fallback_state(m,db,document)
        receipt={**preview,'status':'PASS_SOURCE_ONLY','kind':'EXACT_NATIVE_WHOLE_TEMPLATE_FALLBACK','preview':n.ref(m,a.preview),'afterCounts':after,'physicalProtectionRowsAdded':added,
                 'selectedOwnersRemoved':1,'nativeRowsChanged':0,'processedQueueRowsReset':0,'completedQueueRowsRetired':0,'objectsClassificationChanged':False}
        output=plan/'full/native-whole-template-fallbacks'/(a.expected_preview_sha256+'.json');immutable_json(m,output,receipt)
        m.meta(db,'nativeWholeTemplateFallback',n.ref(m,output));db.commit()
    finally:db.close()


def retained_functional_visual_proof(m,document):
    m.require(document.get('schemaVersion')==1 and document.get('status')=='PASS_SOURCE_CORRECTION_ONLY' and document.get('kind')=='FINITE_RETAINED_FUNCTIONAL_VISUAL_PROTECTION_CORRECTION_V1' and document.get('ownerGraphTraversal') is False and document.get('applyAuthorization') is False,'Unknown retained-functional visual correction')
    source=bound(m,document['sourceProof']);native=document['native'];owner=document['retainedFunctional'];obj=owner['object'];row=document['protectionRow']
    m.require(source.get('status')=='PASS_SOURCE_CORRECTION_ONLY' and source.get('sourceBinding')==document['sourceBinding'] and all(source[key]==document[key] for key in ('native','retainedFunctional','protectionRow','pointSnapshot')),'Exact retained-functional source fields changed')
    refs=source['nativeAndOwnerSourceProofs'];cfacts=bound(m,refs['nativeSource']);pfacts=bound(m,refs['retainedFunctionalSource']);bound(m,refs['diagnostic'])
    m.require(cfacts['sourceBinding']==pfacts['sourceBinding']==document['sourceBinding'] and cfacts['nativeObject']==native['object'] and cfacts['nativePoseDataSha256']==native['poseDataSha256'] and cfacts['runtimeMemberCells']==native['runtimeMemberCells'] and cfacts['physicalMembers']==native['physicalMembers'] and cfacts['visualCells']==native['visualCells'] and cfacts['visualPointDemands']==native['visualPointDemands'],'Actual C complete visual/member source proof changed')
    original=pfacts['protectedObject']
    m.require(original['object']==obj and original['poseDataSha256']==owner['poseDataSha256'] and original['root']==owner['root'] and original['runtimeMemberCells']==owner['runtimeMemberCells'] and original['runtimeMembers']==owner['physicalMembers'] and original['rootBE']==owner['rootBE'],'Original P root/member/payload/ledger source facts changed')
    m.require(obj['kind']=='static' and (obj['dim'],obj['uuid'],obj['pose'])==(owner['dimension'],owner['uuid'],owner['pose']) and owner['root']==[obj[key] for key in ('x','y','z')],'Retained P must keep its exact original static classification/root')
    members={tuple(at) for at in owner['runtimeMemberCells']};visual={tuple(at) for at in native['visualCells']};at=[row[key] for key in ('x','y','z')]
    m.require(0<len(members)==len(owner['runtimeMemberCells'])<=4096 and tuple(owner['root']) in members and len(owner['physicalMembers'])==len(members) and {tuple(p['position']) for p in owner['physicalMembers']}==members and not members&visual,'Every original P physical member must stay outside convertible C visual cells')
    m.require(native['object']['kind']=='substrate' and (native['object']['dim'],native['object']['uuid'],native['object']['pose'])==(native['dimension'],native['uuid'],native['pose']) and native['root']==[native['object'][key] for key in ('x','y','z')] and 0<len(visual)==len(native['visualCells'])<=4096 and tuple(at) in visual and native['dimension']==owner['dimension'],'Correction must concern the actual ordinary C visual footprint')
    m.require(set(row)=={'dim','x','y','z','uuid','reason'} and row['reason']=='functional_guest' and (row['dim'],row['uuid'])==(owner['dimension'],owner['uuid']) and tuple(at) not in members,'Only this P visual-only reservation may be removed')
    target=document['pointSnapshot'];m.require(target['position']==at and target['dimension']==row['dim'] and target['protected_cells']==[row] and not target['bridge_cells'] and not target['placements'],'Unknown/extra protection or a carrier cannot use this exact correction')
    m.require(pfacts['target']['position']==at and pfacts['target']['pointDemand']==target and pfacts['target']['sourceBE'].get('exists') is False and not pfacts['target']['PStoredContributions'] and not pfacts['target']['POldMembers'] and pfacts['target']['isPPhysical'] is False,'The correction target is not a proved empty-of-P visual-only slot')
    m.require(pfacts['summary']['targetNativeRecipeHasPOrigin'] is False,'Original P native membership cannot be released')
    m.require(owner['rootBE'].get('exists') is True and owner['rootBE'].get('id')=='bloodborne_dw:composite_cell' and owner['rootBE'].get('privateOuterFields')==[] and all(original[key] is True for key in ('rootResidentUuidMatches','rootResidentRootMatches','rootResidentRegistryMatches','payloadShiftMatches','payloadMountMatches')) and original['payloadImportedTypeVersion']==1,'Exact retained P resident/payload/private-field proof is missing')
    for member in owner['physicalMembers']:
        m.require(member['storedContributions'] and any(item['uuid']==owner['uuid'] for item in member['storedContributions']),'Every original P member requires its own stored ledger reference')
    mechanism=owner['sourceMechanism'];caps=pfacts['sourceClassCapability']['rows']
    m.require(len(caps)==1 and mechanism['className']==caps[0]['className']=='dev.dreamwalker.bloodbornedw.composite.ImportedRootBlock' and mechanism['wholeModelAndMemberLifecyclePreserved'] is True and mechanism['rootResidentPayloadAndLedgerPreserved'] is True and mechanism['nonV12'] is True and mechanism['exactManifestPoseMatches']==mechanism['exactComponentGroups']==[],'Only this source-proved original P mechanism may be preserved')
    m.require(pfacts['classification']['protectedOriginalV12'] is False and pfacts['manifestPriority']['exactPoseMatches']==pfacts['manifestPriority']['exactComponentGroups']==[],'v12 or confirmed assemblies require their original strict priority')
    semantics=bound(m,source['retainedFunctionalSemantics'])
    m.require(semantics.get('status')=='PASS_SOURCE_REVIEW_NOT_EXECUTION' and semantics.get('sourceBinding')==document['sourceBinding'] and semantics.get('retainedFunctionalUuid')==owner['uuid'] and semantics.get('pose')==owner['pose'] and semantics.get('runtimeMemberCells')==owner['runtimeMemberCells'] and all(semantics.get(key) is True for key in ('originalRootResidentPayloadLedgerPreserved','wholeModelAndMemberLifecyclePreserved','targetOutsideRuntimeMembers','protectedV12Unchanged')) and semantics.get('gameplayPass') is False,'Retained P exact original lifecycle evidence is missing')
    retention=owner['preflightRetention'];reason=pfacts['functionReason']
    m.require(retention['proof']==refs['retainedFunctionalSource'] and retention['sourcePreflightBinding']==reason['preflightBinding'] and retention['protectedRow']==row and tuple(retention['position']) not in visual and retention['position']!=at,'The original functional/v12 trigger must remain outside C visual cells')
    m.require(any(item['cell']==retention['position'] and item['registryId']==retention['triggerState']['registryId'] and item['properties']==retention['triggerState']['properties'] and owner['uuid'] in item['directlyConflictingOwners'] for item in reason['exactSavedConflicts']),'The original preflight retention trigger changed')
    bound(m,retention['sourcePreflightBinding']);history=bound(m,document['historicalStrictOriginalCheck'])
    m.require(history['sourceBinding']==document['sourceBinding'] and history['strictOriginalTargetMatches']==[] and history['targetPosition']==at,'Strict original history must be an exact target-negative proof')
    m.require(document['finalCoverageRequired']=={'nativeFunctions':True,'carrierAudit':True,'affectedGameplayQa':True} and document.get('gameplayPass') is False,'Source correction cannot grant final acceptance')
    return source,pfacts,history


def retained_functional_visual_state(m,db,document,finalized=False):
    source,pfacts,history=retained_functional_visual_proof(m,document);owner=document['retainedFunctional'];obj=owner['object'];token=(owner['dimension'],owner['uuid'])
    m.require(n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',token)==[obj],'Retained P original kind/reason/pose/root changed')
    pose=db.execute('SELECT data FROM poses WHERE key=?',(owner['pose'],)).fetchone();classification=bound(m,m.meta(db,'classification'))
    m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==owner['poseDataSha256'] and m.json.loads(pose[0])['registryId'] not in classification.get('protectedRegistryIds',[]),'P exact source pose must remain non-v12 and unchanged')
    for table in ('selected_objects','fragments','whole_cells','activated_native','cleanup_owners'):
        m.require(not db.execute('SELECT 1 FROM '+table+' WHERE dim=? AND uuid=? LIMIT 1',token).fetchone(),'Retained P acquired conversion/cleanup work: '+table)
    for member in owner['physicalMembers']:
        current=point(m,db,token[0],member['position']);saved=member['pointDemand']
        m.require(all(not current[table] and not saved[table] for table in ('queue','fragments','substrate_fragments','native_visual_fragments','whole_cells','native_visual_roots','bridge_cells','placements')),'Retained P physical member has replacement work')
        own=[row for row in saved['protected_cells'] if row['uuid']==owner['uuid'] and row['reason']=='functional_guest']
        m.require(len(own)==1 and own[0] in current['protected_cells'],'Retained P actual physical protection disappeared')
    retention=owner['preflightRetention'];m.require(retention['sourcePreflightBinding']==m.meta(db,'functionalGuestPreflight'),'The original functional preflight binding changed')
    m.require(retention['triggerState']['registryId'] in classification.get('protectedRegistryIds',[]),'Original v12 retention trigger lost strict classification')
    m.require(db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=? AND reason=?',(token[0],*retention['position'],token[1],'functional_guest')).fetchone(),'Original functional/v12 trigger protection disappeared')
    at=document['pointSnapshot']['position'];correction_strict_history(m,db,token[0],at,history['latestCommittedReceipt'])
    if finalized:
        rows=n.row_dict(db,'SELECT * FROM placements WHERE dim=? AND x=? AND y=? AND z=?',(token[0],*at))
        m.require(len(rows)==1 and type(rows[0]['type_index']) is int and rows[0]['type_index']>=0 and rows[0]['source_state']==m.canonical(pfacts['target']['sourceState']),'Corrected target needs its actual typed ordinary placement and exact source state')
        m.require(not db.execute('SELECT 1 FROM whole_cells WHERE dim=? AND x=? AND y=? AND z=?',(token[0],*at)).fetchone() and not db.execute('SELECT 1 FROM bridge_cells WHERE dim=? AND x=? AND y=? AND z=?',(token[0],*at)).fetchone(),'Corrected target cannot become a whole role or fabricated carrier')


def validate_retained_functional_visual_corrections(m,db,finalized=False,source_files_validator=None):
    check_sources=source_files_validator or (lambda files:require_source_files(m,db,files));reference=m.meta(db,'nativeRetainedFunctionalVisualCorrection');seen=set()
    while reference:
        m.require(reference['sha256'] not in seen,'Retained-functional visual correction history cycle');seen.add(reference['sha256'])
        receipt=bound(m,reference);document=bound(m,receipt['certificate'])
        m.require(receipt.get('status')=='PASS_SOURCE_CORRECTION_ONLY' and receipt.get('kind')=='EXACT_RETAINED_FUNCTIONAL_VISUAL_PROTECTION_CORRECTION' and receipt['sourceBinding']==document['sourceBinding']==m.meta(db,'binding'),'Retained-functional correction receipt changed')
        check_sources(receipt['sourceFiles']);retained_functional_visual_state(m,db,document,finalized)
        row=document['protectionRow'];m.require(not db.execute('SELECT 1 FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=?',(row['dim'],row['x'],row['y'],row['z'])).fetchone(),'Corrected functional visual reservation reentered protection')
        reference=receipt['previousCorrection']


def retained_functional_visual_preview(a,m,db):
    plan=m.checked(a.plan);n.validate_resume(m,db,plan);active_binding(m,db)
    reference={'path':str(m.checked(a.certificate)),'sha256':a.certificate_sha256};document=bound(m,reference);_,pfacts,history=retained_functional_visual_proof(m,document)
    m.require(document['sourceBinding']==m.meta(db,'binding'),'Retained-functional correction source changed')
    failure,archives,files=failed_evidence(m,db,document['failedRun']);require_source_files(m,db,files);native=document['native']
    log=m.checked(failure['log']).read_text(encoding='utf-8',errors='replace')
    m.require('Required native visual overhang meets a protected local object' in log and native['uuid'] in log,'Correction must bind its actual failed ordinary C')
    m.require(m.meta(db,'nativeComposeCursor')['phase']=='NATIVE_TRANSFER' and n.row_dict(db,'SELECT * FROM objects WHERE dim=? AND uuid=?',(native['dimension'],native['uuid']))==[native['object']],'Actual C must remain its exact ordinary source object')
    pose=db.execute('SELECT data FROM poses WHERE key=?',(native['pose'],)).fetchone();m.require(pose and m.hashlib.sha256(pose[0].encode()).hexdigest()==native['poseDataSha256'],'Actual C pose changed')
    m.require(not db.execute('SELECT 1 FROM activated_native WHERE dim=? AND uuid=?',(native['dimension'],native['uuid'])).fetchone(),'This correction cannot retract C activation')
    visual={tuple(at) for at in native['visualCells']};snapshots={tuple(row['position']):row for row in native['visualPointDemands']};target=tuple(document['pointSnapshot']['position'])
    m.require(set(snapshots)==visual and len(snapshots)==len(native['visualPointDemands']) and target in visual,'Every C visual point must be bound exactly')
    for at,saved in snapshots.items():
        actual=point(m,db,native['dimension'],at);m.require(actual==saved and not actual['whole_cells'],'Actual complete C visual/whole clearance changed')
        m.require(actual['protected_cells']==([document['protectionRow']] if at==target else []),'Another protected owner/cell blocks this exact correction')
    m.require(snapshots[target]==document['pointSnapshot'] and m.meta(db,'nativeLocalRetention')==history['latestCommittedReceipt'],'Review the exact target and current strict history')
    retained_functional_visual_state(m,db,document)
    return {'schemaVersion':1,'status':'EXACT_RETAINED_FUNCTIONAL_VISUAL_CORRECTION_PREVIEW','sourceBinding':document['sourceBinding'],'semanticBindings':n.snapshot_bindings(m,db),
            'sourceFiles':source_files(m),'certificate':reference,'beforeCounts':n.counts(db),'cursorBefore':m.meta(db,'nativeComposeCursor'),'retentionBefore':m.meta(db,'nativeLocalRetention'),
            'retainedComponentsBefore':m.meta(db,'nativeRetainedComponents'),'previousCorrection':m.meta(db,'nativeRetainedFunctionalVisualCorrection'),'protectionRow':document['protectionRow'],
            'evidence':{'failedRun':document['failedRun'],'failedSource':archives['migrate.py']},'finalCoverageRequired':document['finalCoverageRequired'],'gameplayPass':False,'worldReads':0,'mapWrites':0}


def correct_retained_functional_visual(a,m):
    plan=m.checked(a.plan);db=sqlite3.connect('file:'+(plan/'plan.sqlite').as_posix()+('?mode=rw' if a.execute else '?mode=ro'),uri=True)
    try:
        db.execute('PRAGMA cache_size=-2048')
        if a.execute:db.execute('PRAGMA synchronous=FULL')
        db.execute('BEGIN IMMEDIATE' if a.execute else 'BEGIN')
        if a.execute:
            m.require(a.expected_preview_sha256 and m.sha(a.preview)==a.expected_preview_sha256,'Execute only the exact reviewed retained-functional correction preview')
            previous=m.meta(db,'nativeRetainedFunctionalVisualCorrection')
            if previous and bound(m,previous)['preview']['sha256']==a.expected_preview_sha256:n.validate_resume(m,db,plan);db.rollback();return
        preview=retained_functional_visual_preview(a,m,db)
        if not a.execute:immutable_json(m,a.preview,preview);return
        m.require(preview==m.read(a.preview),'Retained-functional correction changed after reviewed preview');row=preview['protectionRow']
        m.require(db.execute('DELETE FROM protected_cells WHERE dim=? AND x=? AND y=? AND z=? AND uuid=? AND reason=?',tuple(row[key] for key in ('dim','x','y','z','uuid','reason'))).rowcount==1,'Delete exactly one proven P visual-only functional reservation')
        m.issue(db,'retained_functional_visual_only_protection_correction',row['dim'],(row['x'],row['y'],row['z']),{'uuid':row['uuid'],'certificate':preview['certificate'],'reason':'Only this nonphysical visual reservation is removed; original P root/physical members/payload/ledger and v12 trigger remain unchanged','gameplayPass':False})
        m.require(n.counts(db)==preview['beforeCounts'] and n.snapshot_bindings(m,db)==preview['semanticBindings'] and m.meta(db,'nativeComposeCursor')==preview['cursorBefore'] and m.meta(db,'nativeLocalRetention')==preview['retentionBefore'] and m.meta(db,'nativeRetainedComponents')==preview['retainedComponentsBefore'],'One-row functional correction changed unrelated work/retention/cursor/bindings')
        receipt={**preview,'status':'PASS_SOURCE_CORRECTION_ONLY','kind':'EXACT_RETAINED_FUNCTIONAL_VISUAL_PROTECTION_CORRECTION','preview':n.ref(m,a.preview),'afterCounts':n.counts(db),'protectedRowsDeleted':1,'queueRowsChanged':0,'selectedRowsChanged':0,'nativeRowsChanged':0,'processedQueueRowsReset':0,'objectsClassificationChanged':False}
        output=plan/'full/retained-functional-visual-corrections'/(a.expected_preview_sha256+'.json');immutable_json(m,output,receipt);m.meta(db,'nativeRetainedFunctionalVisualCorrection',n.ref(m,output));db.commit()
    finally:db.close()
