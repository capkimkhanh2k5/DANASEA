"""Generate original blank DEVELOPMENT checklists, not safety certificates.

No operational thresholds, third-party logos, seals, signatures or legal claims.
Only produces new service_safety_documents files; original media is untouched.
"""
from pathlib import Path
import hashlib
import io
import json
import uuid
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
MARKER = 'SAMPLE DEVELOPMENT - KHONG PHAI GIAY CHUNG NHAN'


def main():
    def load(name):
        return json.loads((ROOT/'data'/name).read_text(encoding='utf8'))
    def save(name, value):
        (ROOT/'data'/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    services=load('services.json')['records']; categories={r['sqlRow']['id']:r['sqlRow'] for r in load('categories.json')['records']}
    previous_path=ROOT/'data/service_safety_documents.json'
    previous={r['sqlRow']['id']:r for r in load('service_safety_documents.json')['records']} if previous_path.exists() else {}
    folder=ROOT/'media/service_safety_documents'; folder.mkdir(parents=True,exist_ok=True)
    font_paths=[Path('C:/Windows/Fonts/arial.ttf'),
                Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'),
                Path('/System/Library/Fonts/Supplemental/Arial.ttf')]
    def font(size):
        path=next((p for p in font_paths if p.is_file()),None)
        if path is None:raise ValueError('Install an Arial or DejaVu Sans font to generate new samples')
        return ImageFont.truetype(str(path),size)
    records=[]; media=load('media_index.json')['records']
    accepted_media={m['path']:m for m in media if m['kind']=='service_safety_documents'}
    media=[m for m in media if m['kind']!='service_safety_documents']
    for item in services:
        s=item['sqlRow']; c=categories[s['category_id']]
        if not (s.get('weather_sensitive') is True or c.get('requires_safety_cert') is True):continue
        sid=s['id']; doc_id=str(uuid.uuid5(uuid.NAMESPACE_URL,'danasea:development:service-safety:checklist-v1:'+sid))
        old=previous.get(doc_id)
        path=folder/(sid+'.png')
        if old:
            assert old['sqlRow']['service_id']==sid, 'Accepted sample service mapping changed'
            assert old['sqlRow']['status']=='PENDING' and old['sqlRow']['reviewed_at'] is None, 'Do not reset reviewed documents'
            digest=hashlib.sha256(path.read_bytes()).hexdigest()
            entry=accepted_media.get(path.relative_to(ROOT).as_posix())
            assert old['mediaSha256']==digest and entry and entry['sha256']==digest, 'Accepted sample content changed'
            records.append(old)
            media.append(entry)
            continue
        slug=c['slug']
        if any(k in slug for k in ('sup','kayak','luot-song','surf')):
            prompts=['Equipment inspection reference','Route / activity area reference','Guide briefing reference','Participant readiness review entry']
        elif 'lan' in slug:
            prompts=['Dive / snorkel briefing reference','Equipment review entry','Guide qualification review reference','Participant readiness review entry']
        elif 'du-bay' in slug:
            prompts=['Harness inspection reference','Towboat / operator review reference','Briefing reference','Weather policy review reference']
        elif 'jetski' in slug or 'phao' in slug:
            prompts=['Craft / equipment inspection reference','Activity-zone policy reference','Operator briefing reference','Participant readiness review entry']
        else:
            prompts=['Trip briefing reference','Boat / deck inspection reference','Night-operation plan reference if applicable','Emergency contact review entry']
        image=Image.new('RGB',(1600,2000),'white'); draw=ImageDraw.Draw(image)
        draw.rectangle((0,0,1600,225),fill='#7c1f24')
        draw.text((80,48),'DANASEA - DEVELOPMENT SAMPLE',font=font(53),fill='white')
        draw.text((80,130),'BLANK REVIEW CHECKLIST / NOT A CERTIFICATE',font=font(37),fill='white')
        y=280
        def line(text,size=30,color='#162d3a',gap=18):
            nonlocal y
            words=text.split(); current=''
            for word in words:
                trial=(current+' '+word).strip()
                if draw.textlength(trial,font=font(size))>1420 and current:
                    draw.text((80,y),current,font=font(size),fill=color); y+=size+gap; current=word
                else:current=trial
            if current:draw.text((80,y),current,font=font(size),fill=color);y+=size+gap
        line('MẪU KIỂM THỬ ĐỒ ÁN - KHÔNG PHẢI CHỨNG NHẬN AN TOÀN',38,'#a12128')
        line('Service: '+s['name'],35)
        line('Service UUID: '+sid,26)
        line('Category: '+slug,29)
        line('Fixture document UUID: '+doc_id,26)
        line('Classification: DEVELOPMENT_SYNTHETIC / SAMPLE',28)
        line('Initial workflow state: PENDING - no reviewer or approval',28)
        y+=28
        line('The fields below are deliberately blank test inputs.',31)
        line('They do not describe completed inspections or verified qualifications.',29)
        for prompt in prompts:
            y+=15;line('[  ] '+prompt+': NOT SUPPLIED',30)
        y+=28
        for text in ['Reviewer / organisation: NOT ASSIGNED',
                     'Supporting evidence: NOT SUPPLIED',
                     'Operating thresholds: NOT SPECIFIED - require qualified review',
                     'No signature, seal, certification number or legal validity.',
                     'Do not use this file as operating instructions or proof of safety.']:
            line(text,28,'#7c1f24' if text.startswith('No signature') else '#162d3a')
        assert y<1800,'Checklist text would overflow'
        draw.rectangle((60,1840,1540,1950),outline='#a12128',width=4)
        draw.text((88,1876),MARKER,font=font(31),fill='#a12128')
        path=folder/(sid+'.png')
        stream=io.BytesIO();image.save(stream,format='PNG',optimize=True);binary=stream.getvalue()
        digest=hashlib.sha256(binary).hexdigest()
        old=previous.get(doc_id)
        if old:
            assert old['mediaSha256']==digest and old['sqlRow']['service_id']==sid, 'Accepted sample content changed; use an explicitly reviewed new fixture version'
            assert old['sqlRow']['status']=='PENDING' and old['sqlRow']['reviewed_at'] is None, 'Do not reset reviewed documents'
        if path.exists():assert hashlib.sha256(path.read_bytes()).hexdigest()==digest, 'Do not overwrite accepted media'
        else:path.write_bytes(binary)
        media.append(dict(path=path.relative_to(ROOT).as_posix(),sha256=digest,kind='service_safety_documents',
                          classification='DEVELOPMENT_SYNTHETIC_BLANK_CHECKLIST_NOT_CERTIFICATION',
                          author='DANASEA project development fixture',publicReuse='ORIGINAL_PROJECT_FIXTURE_NO_THIRD_PARTY_ASSETS'))
        records.append(old if old else dict(sqlRow=dict(id=doc_id,service_id=sid,file_url=None,status='PENDING',reviewed_by=None,
                            reviewed_at=None,rejection_reason=None,created_at=None,updated_at=None),
                            mediaSha256=digest,classification='DEVELOPMENT_SYNTHETIC',
                            samplePurpose='Blank UI/workflow test checklist; not evidence of legal or safety compliance',
                            serviceCategorySlug=slug,requiredBy=dict(weatherSensitive=s['weather_sensitive'],categoryRequiresSafetyCert=c['requires_safety_cert'])))
    save('service_safety_documents.json',dict(classification='DEVELOPMENT_SYNTHETIC',records=records))
    save('media_index.json',dict(records=media))
    relationships=load('relationships.json');relationships['baselineCounts']['service_safety_documents']=len(records)
    relationships['mediaCounts']['serviceSafetyDocuments']=len(records)
    relationships['serviceSafetyDocumentMapping']=[{'documentId':r['sqlRow']['id'],'serviceId':r['sqlRow']['service_id'],'mediaSha256':r['mediaSha256']} for r in records]
    relationships['serviceSafetyDocumentsClassification']='PENDING_DEVELOPMENT_CHECKLISTS_NOT_LEGAL_OR_SAFETY_CERTIFICATION'
    save('relationships.json',relationships)
    print(json.dumps({'sampleChecklistsCreated':len(records),'mediaFiles':len(media),'reviewStatus':'PENDING'}))


if __name__=='__main__':main()
