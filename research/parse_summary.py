import json

with open("research/reports/stage3_4_5_full_results.json") as f:
    d = json.load(f)

print("=== STAGE 3: ARCHITECTURE RANKING (TOP 12 OF 30) ===")
s3 = sorted(d["stage3_architecture_comparison"].values(), key=lambda x: x["training_oof_metrics_at_tau_star"]["pr_auc"], reverse=True)
print("| Rank | Config ID | Model | Subset | PR-AUC | ROC-AUC | OOF F1 (Tau*) | Brier | ECE |")
print("|---|---|---|---|---:|---:|---:|---:|---:|")
for i, c in enumerate(s3[:15], 1):
    m = c["training_oof_metrics_at_tau_star"]
    th = c["frozen_training_threshold_f1"]
    cid = c["config_id"]
    mod = c["model_name"]
    sub = c["subset_key"]
    print(f"| {i} | {cid} | {mod} | {sub} | {m['pr_auc']:.4f} | {m['roc_auc']:.4f} | {m['f1']:.4f} (th={th:.2f}) | {m['brier']:.4f} | {m['ece']:.4f} |")

print("\n=== STAGE 4: VALIDATION CANDIDATES ON UNSEEN 9 PROJECTS ===")
print("| Candidate ID | Model | Subset | Frozen Tau* | Val PR-AUC | Val ROC-AUC | Val F1 (Tau*) | Val Prec | Val Rec | Val Brier | Val ECE |")
print("|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|")
for k, v in d["stage4_validation_results"].items():
    agg = v["aggregate_metrics_at_tau_star"]
    th = v["frozen_training_threshold"]
    mod = v["model_name"]
    sub = v["feature_subset"]
    print(f"| {k} | {mod} | {sub} | {th:.3f} | {agg['pr_auc']:.4f} | {agg['roc_auc']:.4f} | {agg['f1']:.4f} | {agg['precision']:.4f} | {agg['recall']:.4f} | {agg['brier']:.4f} | {agg['ece']:.4f} |")

print("\n=== STAGE 4: PER-PROJECT BREAKDOWN FOR TOP MODEL (SUB-1__HistGradientBoosting) ===")
top_v = d["stage4_validation_results"]["SUB-1__HistGradientBoosting"]["per_project_breakdown"]
print("| Project | Commits | Defects | Prev | TP | FP | TN | FN | Prec | Rec | F1 | PR-AUC | ROC-AUC | MCC | Brier | ECE |")
print("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
for p, p_data in top_v.items():
    cm = p_data["confusion_matrix_at_tau_star"]
    m = p_data["metrics_at_tau_star"]
    print(f"| {p} | {p_data['total_commits']} | {p_data['defect_commits']} | {p_data['prevalence']:.2%} | {cm['TP']} | {cm['FP']} | {cm['TN']} | {cm['FN']} | {m['precision']:.4f} | {m['recall']:.4f} | {m['f1']:.4f} | {m['pr_auc']:.4f} | {m['roc_auc']:.4f} | {m['mcc']:.4f} | {m['brier']:.4f} | {m['ece']:.4f} |")
