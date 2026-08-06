package com.letovpn.checker;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {

    private final List<ConfigItem> items = new ArrayList<>();

    public void setItems(List<ConfigItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void addItem(ConfigItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    public List<ConfigItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_config, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ConfigItem item = items.get(position);
        h.name.setText(item.name != null ? item.name : "—");
        h.host.setText(item.host + ":" + item.port);

        if (item.working) {
            h.latency.setText(item.latency + " ms");
            h.latency.setTextColor(Color.parseColor("#00E676"));
            h.status.setText("OK");
            h.status.setTextColor(Color.parseColor("#00E676"));
        } else {
            h.latency.setText("—");
            h.latency.setTextColor(Color.parseColor("#8B949E"));
            h.status.setText("FAIL");
            h.status.setTextColor(Color.parseColor("#FF5252"));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, host, latency, status;

        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.configName);
            host = v.findViewById(R.id.configHost);
            latency = v.findViewById(R.id.configLatency);
            status = v.findViewById(R.id.configStatus);
        }
    }
}
