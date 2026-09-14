from dataclasses import dataclass
from pathlib import Path
import argparse, yaml
from app.config import get_settings

@dataclass(frozen=True)
class SourceSpec:
    id: str; dataset: str; grain: str; required: bool; candidates: tuple[str,...]

def repo_root() -> Path:
    return Path(__file__).resolve().parents[3]

def manifest_path() -> Path:
    return repo_root()/"data"/"moveinsync"/"manifest.yaml"

def data_root() -> Path:
    p=get_settings().data_root
    return p if p.is_absolute() else (repo_root()/p).resolve()

def load_manifest():
    raw=yaml.safe_load(manifest_path().read_text())
    specs=[SourceSpec(str(x['id']),str(x['dataset']),str(x['grain']),bool(x.get('required',True)),tuple(x['candidates'])) for x in raw['sources']]
    return str(raw['version']),specs

def resolve_all():
    version,specs=load_manifest(); resolved=[]; missing=[]; root=data_root()
    for s in specs:
        found=next((root/n for n in s.candidates if (root/n).is_file()),None)
        (resolved if found else missing).append((s,found) if found else s)
    return version,resolved,missing

def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--check',action='store_true'); args=parser.parse_args()
    version,resolved,missing=resolve_all(); print(f"Dataset: {version}\nRoot: {data_root()}")
    for s,p in resolved: print(f"OK   {s.id:<12} {p.name}")
    for s in missing: print(f"MISS {s.id:<12} {', '.join(s.candidates)}")
    return 2 if args.check and missing else 0

if __name__=='__main__': raise SystemExit(main())
